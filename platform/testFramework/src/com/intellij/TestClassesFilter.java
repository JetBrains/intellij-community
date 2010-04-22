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

/**
 * Encapsulates logic of filtering test classes (classes that contain test-cases).
 * <p/>
 * We want to have such facility in order to be able to execute different sets of tests like <code>'fast tests'</code>,
 * <code>'problem tests'</code> etc.
 * <p/>
 * I.e. assumed usage scenario is to create object of this class with necessary filtering criteria and use it's
 * {@link #matches(String, String)} method for determining if particular test should be executed.
 * <p/>
 * The filtering is performed by fully-qualified test class name. There are two ways to define the criteria at the moment:
 * <ul>
 *   <li>
 *     Define target class name filters (at regexp format) explicitly using
 *    {@link #TestClassesFilter(List) dedicated constructor};
 *   </li>
 *   <li>Read class name filters (at regexp format) from the given stream - see {@link #createOn(InputStreamReader)};</li>
 * </ul>
 */
public class TestClassesFilter {

  private final Map<String, List<Pattern>> myPatterns = new HashMap<String, List<Pattern>>();
  public static final TestClassesFilter EMPTY_CLASSES_FILTER = new TestClassesFilter(new HashMap<String, List<String>>());
  public static final ArrayList<Pattern> EMPTY_LIST = new ArrayList<Pattern>();
  private final List<Pattern> myAllPatterns = new ArrayList<Pattern>();

  /**
   * Holds reserved test group name that serves as a negation of matching result.
   *
   * @see #matches(String, String)
   */
  public static final String ALL_EXCLUDE_DEFINED = "ALL_EXCLUDE_DEFINED";

  private TestClassesFilter(Map<String, List<String>> filters) {

    for (String groupName : filters.keySet()) {
      List<String> filterList = filters.get(groupName);
      addPatterns(groupName, filterList);
    }
  }

  /**
   * Creates new <code>TestClassesFilter</code> object with the given list of matching patterns (at regexp format).
   *
   * @param filterList    list of test class matching patterns
   */
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

  /**
   * Creates <code>TestClassesFilter</code> object assuming that the given stream contains grouped test class filters
   * at the following format:
   * <p/>
   * <ul>
   *   <li>
   *      every line that starts with <code>'['</code> symbol and ends with <code>']'</code> symbol defines start
   *      of the new test group. That means that all test class filters that follows this line belongs to the same
   *      test group which name is defined by the text contained between <code>'['</code> and <code>']'</code>
   *   </li>
   *   <li>every line that is not a test-group definition is considered to be a test class filter at regexp format;</li>
   * </ul>
   * <p/>
   * <b>Example</b>
   * Consider that given stream points to the following data:
   * <pre>
   *    [CVS]
   *    com.intellij.cvsSupport2.*
   *    [STRESS_TESTS]
   *    com.intellij.application.InspectionPerformanceTest
   *    com.intellij.application.TraverseUITest
   * </pre>
   * <p/>
   * It defines two test groups:
   * <ul>
   *   <li><b>CVS</b> group with the single test class name pattern <code>'com.intellij.cvsSupport2.*'</code>;</li>
   *   <li>
   *     <b>STRESS_TESTS</b> group with the following test class name patterns:
   *     <ul>
   *       <li>com.intellij.application.InspectionPerformanceTest</li>
   *       <li>com.intellij.application.TraverseUITest</li>
   *     </ul>
   *   </li>
   * </ul>
   * <p/>
   * This method doesn't suppose itself to be owner of the given stream reader, i.e. it assumes that the stream should be
   * closed on caller side.
   *
   * @param inputStreamReader   reader that points to the target test groups config
   * @return                    newly created {@link TestClassesFilter} object with the data contained at the given reader
   * @see #matches(String, String)
   */
  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
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

  /**
   * Allows to check if given class name belongs to the test group with the given name based on filtering rules encapsulated
   * at the current {@link TestClassesFilter} object. I.e. this method returns <code>true</code> if given test class name
   * is matched with any test class name filter configured for the test group with the given name.
   * <p/>
   * <b>Note:</b> there is a special case processing when given group name is {@link #ALL_EXCLUDE_DEFINED}. This method
   * returns <code>true</code> only if all registered patterns (for all test groups) don't match given test class name.
   *
   * @param className   target test class name to check
   * @param groupName   target test group name to check
   * @return            <code>true</code> if given test group name is defined (not <code>null</code>) and test class with given
   *                    name belongs to the test group with given name;
   *                    <code>true</code> if given group if undefined or equal to {@link #ALL_EXCLUDE_DEFINED} and given test
   *                    class name is not matched by all registered patterns;
   *                    <code>false</code> otherwise
   */
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
    return groupName == null || ALL_EXCLUDE_DEFINED.equalsIgnoreCase(groupName.trim());
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
