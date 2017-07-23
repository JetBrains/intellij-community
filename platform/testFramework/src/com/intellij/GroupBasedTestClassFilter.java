/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Encapsulates logic of filtering test classes (classes that contain test-cases).
 * <p/>
 * We want to have such facility in order to be able to execute different sets of tests like {@code 'fast tests'},
 * {@code 'problem tests'} etc.
 * <p/>
 * I.e. assumed usage scenario is to create object of this class with necessary filtering criteria and use it's
 * {@link TestClassesFilter#matches(String, String)} method for determining if particular test should be executed.
 * <p/>
 * The filtering is performed by fully-qualified test class name. There are two ways to define the criteria at the moment:
 * <ul>
 *   <li>
 *     Define target class name filters (at regexp format) explicitly using
 *     {@link PatternListTestClassFilter#PatternListTestClassFilter(List) PatternListTestClassFilter};
 *   </li>
 *   <li>
 *     Read class name filters (at regexp format) from the given stream - see {@link #createOn(Reader, List)};
 *   </li>
 * </ul>
 */
public class GroupBasedTestClassFilter extends TestClassesFilter {
  /**
   * Holds reserved test group name that serves as a negation of matching result.
   *
   * @see TestClassesFilter#matches(String, String)
   */
  public static final String ALL_EXCLUDE_DEFINED = "ALL_EXCLUDE_DEFINED";

  private final List<Group> myGroups = ContainerUtil.newSmartList();
  private final Set<String> myTestGroupNames;

  public GroupBasedTestClassFilter(MultiMap<String, String> filters, List<String> testGroupNames) {
    //empty group means all patterns from each defined group should be excluded
    myTestGroupNames = ContainerUtil.newTroveSet(testGroupNames);

    for (String groupName : filters.keySet()) {
      Collection<String> groupFilters = filters.get(groupName);
      List<Pattern> includePatterns = compilePatterns(ContainerUtil.filter(groupFilters, s -> !s.startsWith("-")));
      List<Pattern> excludedPatterns = compilePatterns(groupFilters.stream()
                                                         .filter(s -> s.startsWith("-") && s.length() > 1)
                                                         .map(s -> s.substring(1))
                                                         .collect(Collectors.toList()));
      myGroups.add(new Group(groupName, includePatterns, excludedPatterns));
    }
  }

  /**
   * Creates {@code TestClassesFilter} object assuming that the given stream contains grouped test class filters
   * at the following format:
   * <p/>
   * <ul>
   *   <li>
   *      every line that starts with {@code '['} symbol and ends with {@code ']'} symbol defines start
   *      of the new test group. That means that all test class filters that follows this line belongs to the same
   *      test group which name is defined by the text contained between {@code '['} and {@code ']'}
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
   *   <li><b>CVS</b> group with the single test class name pattern {@code 'com.intellij.cvsSupport2.*'};</li>
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
   * @param testGroupNames
   * @return newly created {@link GroupBasedTestClassFilter} object with the data contained at the given reader
   * @see TestClassesFilter#matches(String, String)
   */
  @NotNull
  public static TestClassesFilter createOn(@NotNull Reader reader, @NotNull List<String> testGroupNames) throws IOException {
    return new GroupBasedTestClassFilter(readGroups(reader), testGroupNames);
  }

  public static MultiMap<String, String> readGroups(Reader reader) throws IOException {
    MultiMap<String, String> groupNameToPatternsMap = MultiMap.createLinked();
    String currentGroupName = "";

    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"}) BufferedReader bufferedReader = new BufferedReader(reader);
    String line;
    while ((line = bufferedReader.readLine()) != null) {
      if (line.startsWith("#")) continue;
      if (line.startsWith("[") && line.endsWith("]")) {
        currentGroupName = line.substring(1, line.length() - 1);
      }
      else {
        groupNameToPatternsMap.putValue(currentGroupName, line);
      }
    }
    return groupNameToPatternsMap;
  }

  /**
   * Allows to check if given class name belongs to the test group with the given name based on filtering rules encapsulated
   * at the current {@link GroupBasedTestClassFilter} object. I.e. this method returns {@code true} if given test class name
   * is matched with any test class name filter configured for the test group with the given name.
   * <p/>
   * <b>Note:</b> there is a special case processing when given group name is {@link #ALL_EXCLUDE_DEFINED}. This method
   * returns {@code true} only if all registered patterns (for all test groups) don't match given test class name.
   *
   * @param className   target test class name to check
   * @param moduleName
   * @return            {@code true} if given test group name is defined (not {@code null}) and test class with given
   *                    name belongs to the test group with given name;
   *                    {@code true} if given group if undefined or equal to {@link #ALL_EXCLUDE_DEFINED} and given test
   *                    class name is not matched by all registered patterns;
   *                    {@code false} otherwise
   */
  @Override
  public boolean matches(String className, String moduleName) {
    if (myGroups.stream().filter(g -> myTestGroupNames.contains(g.name)).anyMatch(g -> g.matches(className))) return true;
    return containsAllExcludeDefinedGroup(myTestGroupNames) && myGroups.stream().noneMatch(g -> g.matches(className));
  }

  private static boolean containsAllExcludeDefinedGroup(Set<String> groupNames) {
    return groupNames.isEmpty() || groupNames.contains(ALL_EXCLUDE_DEFINED);
  }

  private static class Group {
    private final String name;
    private final List<Pattern> included;
    private final List<Pattern> excluded;

    private Group(String name, List<Pattern> included, List<Pattern> excluded) {
      this.name = name;
      this.excluded = excluded;
      this.included = included;
    }
    
    private boolean matches(String className) {
      return !matchesAnyPattern(excluded, className) && matchesAnyPattern(included, className);
    }
  }
}
