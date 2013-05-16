/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Encapsulates logic of filtering test classes (classes that contain test-cases).
 * <p/>
 * We want to have such facility in order to be able to execute different sets of tests like <code>'fast tests'</code>,
 * <code>'problem tests'</code> etc.
 * <p/>
 * I.e. assumed usage scenario is to create object of this class with necessary filtering criteria and use it's
 * {@link #matches(String)} method for determining if particular test should be executed.
 * <p/>
 * The filtering is performed by fully-qualified test class name. There are two ways to define the criteria at the moment:
 * <ul>
 *   <li>
 *     Define target class name filters (at regexp format) explicitly using
 *     {@link PatternListTestClassFilter#PatternListTestClassFilter(List) PatternListTestClassFilter};
 *   </li>
 *   <li>
 *     Read class name filters (at regexp format) from the given stream - see {@link #createOn(Reader, String)};
 *   </li>
 * </ul>
 */
public class GroupBasedTestClassFilter extends TestClassesFilter {
  /**
   * Holds reserved test group name that serves as a negation of matching result.
   *
   * @see #matches(String)
   */
  public static final String ALL_EXCLUDE_DEFINED = "ALL_EXCLUDE_DEFINED";

  private final Map<String, List<Pattern>> myPatterns = new HashMap<String, List<Pattern>>();
  private final List<Pattern> myAllPatterns = new ArrayList<Pattern>();
  private final List<Pattern> myTestGroupPatterns;
  private final String myTestGroupName;

  private GroupBasedTestClassFilter(Map<String, List<String>> filters, String testGroupName) {
    myTestGroupName = testGroupName;

    for (String groupName : filters.keySet()) {
      List<String> filterList = filters.get(groupName);
      addPatterns(groupName, filterList);
    }

    myTestGroupPatterns = collectPatternsFor(myTestGroupName);
  }

  private void addPatterns(String groupName, List<String> filterList) {
    List<Pattern> patterns = compilePatterns(filterList);
    myPatterns.put(groupName, patterns);
    myAllPatterns.addAll(patterns);
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
   *
   * @param reader   reader that points to the target test groups config
   * @param testGroupName
   * @return newly created {@link GroupBasedTestClassFilter} object with the data contained at the given reader
   * @see #matches(String)
   */
  public static TestClassesFilter createOn(Reader reader, String testGroupName) throws IOException {
    Map<String, List<String>> groupNameToPatternsMap = new HashMap<String, List<String>>();
    String currentGroupName = "";

    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"}) BufferedReader bufferedReader = new BufferedReader(reader);
    String line;
    while ((line = bufferedReader.readLine()) != null) {
      if (line.startsWith("#")) continue;
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

    return new GroupBasedTestClassFilter(groupNameToPatternsMap, testGroupName);
  }

  /**
   * Allows to check if given class name belongs to the test group with the given name based on filtering rules encapsulated
   * at the current {@link GroupBasedTestClassFilter} object. I.e. this method returns <code>true</code> if given test class name
   * is matched with any test class name filter configured for the test group with the given name.
   * <p/>
   * <b>Note:</b> there is a special case processing when given group name is {@link #ALL_EXCLUDE_DEFINED}. This method
   * returns <code>true</code> only if all registered patterns (for all test groups) don't match given test class name.
   *
   * @param className   target test class name to check
   * @return            <code>true</code> if given test group name is defined (not <code>null</code>) and test class with given
   *                    name belongs to the test group with given name;
   *                    <code>true</code> if given group if undefined or equal to {@link #ALL_EXCLUDE_DEFINED} and given test
   *                    class name is not matched by all registered patterns;
   *                    <code>false</code> otherwise
   */
  @Override
  public boolean matches(String className) {
    boolean result = matchesAnyPattern(myTestGroupPatterns, className);
    //null group means all patterns from each defined group should be excluded
    if (isAllExcludeDefinedGroup(myTestGroupName)) {
      return !result;
    }
    else {
      return result;
    }
  }

  private static boolean isAllExcludeDefinedGroup(String groupName) {
    return StringUtil.isEmpty(groupName) || ALL_EXCLUDE_DEFINED.equalsIgnoreCase(groupName.trim());
  }

  private List<Pattern> collectPatternsFor(String groupName) {
    if (isAllExcludeDefinedGroup(groupName)) {
      return myAllPatterns;
    }
    else if (myPatterns.containsKey(groupName)) {
      return myPatterns.get(groupName);
    }
    else {
      return Collections.emptyList();
    }
  }
}
