package org.yinwang.pysonar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Test {

    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Analyzer analyzer = new Analyzer();
    String inputDir;
    boolean exp;


    public Test(String inputDir, boolean exp) {
        this.inputDir = inputDir;
        this.exp = exp;
    }


    public void runAnalysis(String dir) {
        analyzer.analyze(dir);
        analyzer.finish();
    }


    public boolean checkRefs() {
        String expecteRefs = _.makePathString(inputDir, "expected_refs");
        if (exp) {

            List<Map<String, Object>> refs = new ArrayList<>();
            for (Map.Entry<Ref, List<Binding>> e : analyzer.getReferences().entrySet()) {

                String file = e.getKey().getFile();

                // only record those in the inputDir
                if (file != null && !file.startsWith("/")) {
                    Map<String, Object> writeout = new LinkedHashMap<>();

                    Map<String, Object> ref = new LinkedHashMap<>();
                    ref.put("name", e.getKey().getName());
                    ref.put("file", file);
                    ref.put("start", e.getKey().start());

                    List<Map<String, Object>> dests = new ArrayList<>();
                    for (Binding b : e.getValue()) {
                        String destFile = b.getFile();
                        if (destFile != null && destFile.startsWith(inputDir)) {
                            destFile = destFile.substring(inputDir.length() + 1);
                            Map<String, Object> dest = new LinkedHashMap<>();
                            dest.put("name", b.getName());
                            dest.put("file", destFile);
                            dest.put("start", b.getStart());
                            dests.add(dest);
                        }
                    }
                    writeout.put("ref", ref);
                    writeout.put("dests", dests);
                    refs.add(writeout);
                }
            }
            String json = gson.toJson(refs);
            _.writeFile(expecteRefs, json);
            return true;


        } else {
            List<Map<String, Object>> failedRefs = new ArrayList<>();
            String json = _.readFile(expecteRefs);
            if (json == null) {
                _.msg("Expected refs not found");
                return false;
            }
            List<Map<String, Object>> expectedRefs = gson.fromJson(json, List.class);
            for (Map<String, Object> r : expectedRefs) {
                Map<String, Object> refMap = (Map<String, Object>) r.get("ref");
                Ref ref = createRef(refMap);

                List<Map<String, Object>> dests = (List<Map<String, Object>>) r.get("dests");
                List<Binding> actualDests = analyzer.getReferences().get(ref);
                List<Map<String, Object>> failedDests = new ArrayList<>();

                for (Map<String, Object> d : dests) {
                    String name = (String) d.get("name");
                    String file = (String) d.get("file");
                    int start = (int) Math.floor((double) d.get("start"));

                    if (!checkBindingExist(actualDests, name, file, start)) {
                        failedDests.add(d);
                    }
                }

                // record the ref & failed dests if any
                if (!failedDests.isEmpty()) {
                    Map<String, Object> failedRef = new LinkedHashMap<>();
                    failedRef.put("ref", refMap);
                    failedRef.put("dests", failedDests);
                    failedRefs.add(failedRef);
                }
            }

            if (failedRefs.isEmpty()) {
                return true;
            } else {
                _.msg("Failed refs: " + gson.toJson(failedRefs));
                return false;
            }
        }
    }


    boolean checkBindingExist(List<Binding> bs, String name, String file, int start) {
        if (bs == null) {
            return false;
        }

        for (Binding b : bs) {
            String actualFile = b.getFile();

            if (actualFile != null && actualFile.startsWith(inputDir)) {
                actualFile = actualFile.substring(inputDir.length() + 1);

                if (b.getName().equals(name) &&
                        actualFile.equals(file) &&
                        b.getStart() == start)
                {
                    return true;
                }
            }
        }
        return false;
    }


    Ref createRef(Map<String, Object> m) {
        String name = (String) m.get("name");
        String file = (String) m.get("file");
        int start = (int) Math.floor((double) m.get("start"));
        return new Ref(name, file, start);
    }


    public static void main(String[] args) {
        String inputDir = _.unifyPath(args[0]);

        // generate expected file?
        boolean exp = false;
        if (args.length > 1 && args[1].equals("-exp")) {
            exp = true;
        }

        Test test = new Test(inputDir, exp);
        test.runAnalysis(inputDir);
        test.checkRefs();

    }
}
