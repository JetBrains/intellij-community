/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.idea.Bombed;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.JITSensitive;
import com.intellij.testFramework.TeamCityLogger;
import com.intellij.testFramework.TestFrameworkUtil;
import com.intellij.util.containers.MultiMap;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral", "UseOfSystemOutOrSystemErr", "CallToPrintStackTrace", "TestOnlyProblems"})
public class TestCaseLoader {
  public static final String PERFORMANCE_TESTS_ONLY_FLAG = "idea.performance.tests";
  public static final String INCLUDE_PERFORMANCE_TESTS_FLAG = "idea.include.performance.tests";
  public static final String INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG = "idea.include.unconventionally.named.tests";

  private static final boolean PERFORMANCE_TESTS_ONLY = System.getProperty(PERFORMANCE_TESTS_ONLY_FLAG) != null;
  private static final boolean INCLUDE_PERFORMANCE_TESTS = System.getProperty(INCLUDE_PERFORMANCE_TESTS_FLAG) != null;
  private static final boolean INCLUDE_UNCONVENTIONALLY_NAMED_TESTS = System.getProperty(INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG) != null;

  /**
   * An implicit group which includes all tests from all defined groups and tests which don't belong to any group.
   */
  private static final String ALL_TESTS_GROUP = "ALL";

  private final List<Class> myClassList = new ArrayList<>();
  private final List<Throwable> myClassLoadingErrors = new ArrayList<>();
  private Class myFirstTestClass;
  private Class myLastTestClass;
  private final TestClassesFilter myTestClassesFilter;
  private final boolean myForceLoadPerformanceTests;

  public TestCaseLoader(String classFilterName) {
    this(classFilterName, false);
  }
  
  public TestCaseLoader(String classFilterName, boolean forceLoadPerformanceTests) {
    myForceLoadPerformanceTests = forceLoadPerformanceTests;
    String patterns = getTestPatterns();
    if (!StringUtil.isEmpty(patterns)) {
      myTestClassesFilter = new PatternListTestClassFilter(StringUtil.split(patterns, ";"));
      System.out.println("Using patterns: [" + patterns +"]");
    }
    else {
      List<URL> groupingFileUrls = Collections.emptyList();
      if (!StringUtil.isEmpty(classFilterName)) {
        try {
          groupingFileUrls = Collections.list(getClassLoader().getResources(classFilterName));
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }

      List<String> testGroupNames = getTestGroups();
      MultiMap<String, String> groups = MultiMap.createLinked();

      for (URL fileUrl : groupingFileUrls) {
        try {
          InputStreamReader reader = new InputStreamReader(fileUrl.openStream());
          try {
            groups.putAllValues(GroupBasedTestClassFilter.readGroups(reader));
          }
          finally {
            reader.close();
          }
        }
        catch (IOException e) {
          e.printStackTrace();
          System.err.println("Failed to load test groups from " + fileUrl);
        }
      }

      if (groups.isEmpty() || testGroupNames.contains(ALL_TESTS_GROUP)) {
        System.out.println("Using all classes");
        myTestClassesFilter = TestClassesFilter.ALL_CLASSES;
      }
      else {
        System.out.println("Using test groups: " + testGroupNames);
        myTestClassesFilter = new GroupBasedTestClassFilter(groups, testGroupNames);
      }
    }
  }

  @Nullable 
  private static String getTestPatterns() {
    return System.getProperty("intellij.build.test.patterns", System.getProperty("idea.test.patterns"));
  }

  @NotNull
  private static List<String> getTestGroups() {
    return StringUtil.split(System.getProperty("intellij.build.test.groups", System.getProperty("idea.test.group", "")).trim(), ";");
  }

  void addClassIfTestCase(Class testCaseClass, String moduleName) {
    if (shouldAddTestCase(testCaseClass, moduleName, true) &&
        testCaseClass != myFirstTestClass && testCaseClass != myLastTestClass &&
        TestFrameworkUtil.canRunTest(testCaseClass)) {
      myClassList.add(testCaseClass);
    }
  }

  void addFirstTest(Class aClass) {
    assert myFirstTestClass == null : "already added: "+aClass;
    assert shouldAddTestCase(aClass, null, false) : "not a test: "+aClass;
    myFirstTestClass = aClass;
  }

  void addLastTest(Class aClass) {
    assert myLastTestClass == null : "already added: "+aClass;
    assert shouldAddTestCase(aClass, null, false) : "not a test: "+aClass;
    myLastTestClass = aClass;
  }

  private boolean shouldAddTestCase(final Class<?> testCaseClass, String moduleName, boolean testForExcluded) {
    if ((testCaseClass.getModifiers() & Modifier.ABSTRACT) != 0) return false;
    if (testForExcluded && shouldExcludeTestClass(moduleName, testCaseClass)) return false;

    if (TestCase.class.isAssignableFrom(testCaseClass) || TestSuite.class.isAssignableFrom(testCaseClass)) {
      return true;
    }
    try {
      final Method suiteMethod = testCaseClass.getMethod("suite");
      if (Test.class.isAssignableFrom(suiteMethod.getReturnType()) && (suiteMethod.getModifiers() & Modifier.STATIC) != 0) {
        return true;
      }
    }
    catch (NoSuchMethodException ignored) { }

    return TestFrameworkUtil.isJUnit4TestClass(testCaseClass);
  }

  private boolean shouldExcludeTestClass(String moduleName, Class testCaseClass) {
    if (!myForceLoadPerformanceTests && !shouldIncludePerformanceTestCase(testCaseClass)) return true;
    String className = testCaseClass.getName();

    return !myTestClassesFilter.matches(className, moduleName) || isBombed(testCaseClass);
  }

  public static boolean isBombed(final AnnotatedElement element) {
    final Bombed bombedAnnotation = element.getAnnotation(Bombed.class);
    if (bombedAnnotation == null) return false;
    return !TestFrameworkUtil.bombExplodes(bombedAnnotation);
  }
  
  public void loadTestCases(final String moduleName, final Collection<String> classNamesIterator) {
    for (String className : classNamesIterator) {
      try {
        Class candidateClass = Class.forName(className, false, getClassLoader());
        addClassIfTestCase(candidateClass, moduleName);
      }
      catch (Throwable e) {
        String message = "Cannot load class " + className + ": " + e.getMessage();
        System.err.println(message);
        myClassLoadingErrors.add(new Throwable(message, e));
      }
    }
  }

  protected ClassLoader getClassLoader() {
    return getClass().getClassLoader();
  }

  public List<Throwable> getClassLoadingErrors() {
    return myClassLoadingErrors;
  }

  private static final List<String> ourRankList = getTeamCityRankList();

  private static List<String> getTeamCityRankList() {
    if (isPerformanceTestsRun()) {
      // let performance test order be stable to decrease the variation in their timings
      return Collections.emptyList();
    }

    String filePath = System.getProperty("teamcity.tests.recentlyFailedTests.file", null);
    if (filePath != null) {
      try {
        return FileUtil.loadLines(filePath);
      }
      catch (IOException ignored) { }
    }

    return Collections.emptyList();
  }

  private static int getRank(Class aClass) {
    if (isPerformanceTestsRun()) {
      return moveToStart(aClass) ? 0 : 1;
    }

    int i = ourRankList.indexOf(aClass.getName());
    if (i != -1) {
      return i;
    }
    return ourRankList.size();
  }

  private static boolean moveToStart(Class testClass) {
    return testClass.getAnnotation(JITSensitive.class) != null;
  }

  public List<Class> getClasses() {
    List<Class> result = new ArrayList<>(myClassList.size());
    result.addAll(myClassList);
    Collections.sort(result, Comparator.comparingInt(TestCaseLoader::getRank));
    
    if (myFirstTestClass != null) {
      result.add(0, myFirstTestClass);
    }
    if (myLastTestClass != null) {
      result.add(myLastTestClass);
    }

    return result;
  }

  public void clearClasses() {
    myClassList.clear();
  }

  static boolean isPerformanceTestsRun() {
    return PERFORMANCE_TESTS_ONLY;
  }

  static boolean isIncludingPerformanceTestsRun() {
    return INCLUDE_PERFORMANCE_TESTS;
  }

  static boolean shouldIncludePerformanceTestCase(Class aClass) {
    return isIncludingPerformanceTestsRun() || isPerformanceTestsRun() || !isPerformanceTest(null,aClass);
  }

  static boolean isPerformanceTest(String methodName, Class aClass) {
    return TestFrameworkUtil.isPerformanceTest(methodName, aClass.getSimpleName());
  }

  public void fillTestCases(String rootPackage, List<File> classesRoots) {
    long before = System.currentTimeMillis();
    for (File classesRoot : classesRoots) {
      int oldCount = getClasses().size();
      ClassFinder classFinder = new ClassFinder(classesRoot, rootPackage, INCLUDE_UNCONVENTIONALLY_NAMED_TESTS);
      loadTestCases(classesRoot.getName(), classFinder.getClasses());
      int newCount = getClasses().size();
      if (newCount != oldCount) {
        System.out.println("Loaded " + (newCount - oldCount) + " tests from class root " + classesRoot);
      }
    }

    if (getClasses().size() == 1) {
      clearClasses();
    }
    long after = System.currentTimeMillis();
    
    String message = "Number of test classes found: " + getClasses().size() 
                      + " time to load: " + (after - before) / 1000 + "s.";
    System.out.println(message);
    TeamCityLogger.info(message);
  }
}
