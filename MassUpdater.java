/*
* Copyright (C) 2024 Pavel Chumakou (pavel.chumakou@gmail.com)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*        http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class MassUpdater {

    private record Action(String action, String parameters){};

    public static void main (String[] args) throws IOException {
        String templateFileName;
        if (args.length == 1) {
            templateFileName = args[0];
        } else {
            System.out.println("Usage: java MassUpdater.java <templateFile>");
            return;
        }

        List<Action> actions = new ArrayList<>();
        Map<String, String> forEachFieldsMap = new LinkedHashMap<>();
        Map<String, String> fieldsMap = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(templateFileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("<!--") || line.startsWith("//") || line.startsWith("--")
                        || line.startsWith("/*") || line.startsWith("'")) {
                    continue; // skip comment line
                }
                if (line.contains(" ")) {
                    String parameters = line.substring(line.indexOf(" ")).trim();
                    if (line.startsWith("print ")) {
                        actions.add(new Action("print", parameters));
                        continue;
                    }
                    if (line.startsWith("save ")) {
                        actions.add(new Action("save", parameters));
                        continue;
                    }
                    if (line.startsWith("mkdir ")) {
                        actions.add(new Action("mkdir", parameters));
                        continue;
                    }
                    if (line.startsWith("foreach ")) {
                        putField(forEachFieldsMap, parameters);
                        continue;
                    }
                }
                if (line.length() > 3 && line.endsWith("={{")) { // multiple lines field
                    String lVal = line.substring(0, line.indexOf("={{")).trim();
                    StringBuilder rVal = new StringBuilder();
                    String nextLine;
                    while ((nextLine = br.readLine()) != null) {
                        if (nextLine.equals("}}")) {
                            break;
                        }
                        rVal.append(nextLine).append("\n");
                    }
                    if (lVal.length() > 0) {
                        fieldsMap.put(lVal, removeLastLf(rVal.toString()));
                    }
                    continue;
                }
                if (line.length() > 2 && line.contains(" =")) { // single line field
                    putField(fieldsMap, line);
                }
            }
        }

        Map<String, String> resolvedFieldsMap = new LinkedHashMap<>();

        if (forEachFieldsMap.size() > 0) { // currently we process only one "foreach" field
            String forEachField = forEachFieldsMap.keySet().iterator().next();
            String[] forEachFieldVals = forEachFieldsMap.get(forEachField).split(",");
            for (String forEachFieldVal : forEachFieldVals) {
                resolvedFieldsMap.clear();
                resolvedFieldsMap.put(forEachField, forEachFieldVal);
                resolveFields(fieldsMap, resolvedFieldsMap, forEachFieldVal);
                executeActions(actions, resolvedFieldsMap);
            }
        } else {
            resolveFields(fieldsMap, resolvedFieldsMap, null);
            executeActions(actions, resolvedFieldsMap);
        }
    }

    private static void executeActions(List<Action> actions, Map<String, String> resolvedFieldsMap) throws IOException {
        for (Action action : actions) {
            if (action.action.equals("print")) {
                System.out.println(resolveField(action.parameters, resolvedFieldsMap));
            }
            if (action.action.equals("save")) {
                String[] parts = action.parameters.split(" to ");
                String data = resolveField(parts[0].trim(), resolvedFieldsMap);
                String path = resolveField(parts[1].trim(), resolvedFieldsMap);
                Files.writeString(Paths.get(path), data);
            }
            if (action.action.equals("mkdir")) {
                System.out.println("mkdir is not implemented yet");
            }
        }
    }

    public static void resolveFields(Map<String, String> fieldsMap,
                                     Map<String, String> resolvedFieldsMap,
                                     String forEachFieldVal) {
        if (fieldsMap.size() > 0) {
            for (String fieldName : fieldsMap.keySet()) {
                String fieldValue = fieldsMap.get(fieldName);
                if (fieldName.contains(".") && forEachFieldVal != null) {
                    String lVal = fieldName.substring(0, fieldName.indexOf(".")).trim();
                    String rVal = fieldName.substring(fieldName.indexOf(".") + 1).trim();
                    if (rVal.equals(forEachFieldVal)) {
                        resolvedFieldsMap.put(lVal, resolveField(fieldValue, resolvedFieldsMap));
                    }
                } else {
                    resolvedFieldsMap.put(fieldName, resolveField(fieldValue, resolvedFieldsMap));
                }
            }
        }
    }

    private static String resolveField(String field, Map<String, String> resolvedFieldsMap) {
        String result = field;
        for (String fieldName : resolvedFieldsMap.keySet()) {
            String fieldValue = resolvedFieldsMap.get(fieldName);
            if (fieldValue.isEmpty()) {
                result = result.replaceAll("\n<%" + fieldName + "%>\n", "\n");
                result = result.replaceAll("\n<%" + fieldName.toUpperCase() + "%>\n", "\n");
            }
            result = result.replaceAll("<%" + fieldName + "%>", Matcher.quoteReplacement(fieldValue));
            result = result.replaceAll("<%" + fieldName.toUpperCase() + "%>", Matcher.quoteReplacement(fieldValue.toUpperCase()));
        }
        return result;
    }

    private static void putField(Map<String, String> map, String line) {
        String lVal = line.substring(0, line.indexOf(" =")).trim();
        //String rVal = line.substring(line.indexOf(" =") + 2).trim();
        String rVal;
        if (line.endsWith(" =")) {
            rVal = "";
        } else {
            rVal = line.substring(line.indexOf(" = ") + 3);
        }
        if (lVal.length() > 0) {
            map.put(lVal, rVal);
        }
    }

    private static String removeLastLf(String string) {
        if (string.endsWith("\n")) {
            return string.substring(0, string.length() - 1);
        }
        return string;
    }

}
