/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.*;
import java.util.regex.Pattern;

public class TestClassesFilter {

  private final Map<String, List<Pattern>> myPatterns = new HashMap<String, List<Pattern>>();
  public static final TestClassesFilter EMPTY_CLASSES_FILTER = new TestClassesFilter(new HashMap<String, List<String>>());
  public static final ArrayList<Pattern> EMPTY_LIST = new ArrayList<Pattern>();
  private final List<Pattern> myAllPatterns = new ArrayList<Pattern>();
  public static final String ALL_EXCLUDE_DEFINED = "ALL_EXCLUDE_DEFINED";

  private TestClassesFilter(Map<String, List<String>> filters) {

    for (String groupName : filters.keySet()) {
      List<String> filterList = filters.get(groupName);
      addPatterns(groupName, filterList);
    }
  }

  TestClassesFilter(List<String> filterList) {
    addPatterns("", filterList);
  }

  private void addPatterns(String groupName, List<String> filterList) {
    ArrayList<Pattern> patterns = new ArrayList<Pattern>();
    myPatterns.put(groupName, patterns);
    for (String aFilter : filterList) {
      String filter = aFilter.trim();
      if (filter.length() == 0) continue;
      filter = filter.replaceAll("\\*", ".\\*");
      Pattern pattern = Pattern.compile(filter);
      myAllPatterns.add(pattern);
      patterns.add(pattern);
    }
  }

  public static TestClassesFilter createOn(InputStreamReader inputStreamReader) {
    try {
      Map<String, List<String>> groupNameToPatternsMap = new HashMap<String, List<String>>();
      String currentGroupName = "";
      LineNumberReader lineNumberReader = new LineNumberReader(inputStreamReader);
      String line;
      while ((line = lineNumberReader.readLine()) != null) {
        if (line.startsWith("[") && line.endsWith("]")) {
          currentGroupName = line.substring(1, line.length() - 1);
        }
        else {
          if (!groupNameToPatternsMap.containsKey(currentGroupName)) {
            groupNameToPatternsMap.put(currentGroupName, new ArrayList<String>());
          }
          groupNameToPatternsMap.get(currentGroupName).add(line);
        }
      }

      return new TestClassesFilter(groupNameToPatternsMap);
    }
    catch (IOException e) {
      return EMPTY_CLASSES_FILTER;
    }
  }

  private static boolean matches(Collection<Pattern> patterns, String className) {
    for (Pattern pattern : patterns) {
      if (pattern.matcher(className).matches()) {
        return true;
      }
    }
    return false;

  }


  public boolean matches(String className, String groupName) {
    List<Pattern> patterns = collectPatternsFor(groupName);
    boolean result = matches(patterns, className);
    //null group means all patterns from each defined group should be excluded
    if (isAllExcludeDefinedGroup(groupName)) {
      return !result;
    }
    else {
      return result;
    }
  }

  private static boolean isAllExcludeDefinedGroup(String groupName) {
    if (groupName == null){
      return true;
    }

    if (ALL_EXCLUDE_DEFINED.equals(groupName)){
      return true;
    }

    return false;
  }

  private List<Pattern> collectPatternsFor(String groupName) {
    if (isAllExcludeDefinedGroup(groupName)){
      return myAllPatterns;
    } else {
      if (!myPatterns.containsKey(groupName)){
        return EMPTY_LIST;
      } else {
        return myPatterns.get(groupName);
      }
    }
  }
}
