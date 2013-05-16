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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:30:35 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.idea.Bombed;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestRunnerUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral", "UseOfSystemOutOrSystemErr", "CallToPrintStackTrace", "TestOnlyProblems"})
public class TestCaseLoader {
  /** Holds name of JVM property that is assumed to define target test group name. */
  private static final String TARGET_TEST_GROUP = "idea.test.group";

  /** Holds name of JVM property that is assumed to define filtering rules for test classes. */
  private static final String TARGET_TEST_PATTERNS = "idea.test.patterns";

  public static final String PERFORMANCE_TESTS_ONLY_FLAG = "idea.performance.tests";
  public static final String SKIP_COMMUNITY_TESTS = "idea.skip.community.tests";

  private final List<Class> myClassList = new ArrayList<Class>();
  private Class myFirstTestClass;
  private Class myLastTestClass;
  private final TestClassesFilter myTestClassesFilter;
  private final boolean myIsPerformanceTestsRun;

  public TestCaseLoader(String classFilterName, boolean isPerformanceTestsRun) {
    myIsPerformanceTestsRun = isPerformanceTestsRun;

    URL excludedStream = StringUtil.isEmpty(classFilterName) ? null : getClass().getClassLoader().getResource(classFilterName);
    if (excludedStream != null) {
      TestClassesFilter filter;
      try {
        String testGroupName = System.getProperty(TARGET_TEST_GROUP, "").trim();
        InputStreamReader reader = new InputStreamReader(excludedStream.openStream());
        try {
          filter = GroupBasedTestClassFilter.createOn(reader, testGroupName);
          System.out.println("Using test group: [" + testGroupName +"]");
        }
        finally {
          reader.close();
        }
      }
      catch (IOException e) {
        e.printStackTrace();
        filter = TestClassesFilter.ALL_CLASSES;
        System.out.println("Using all classes");
      }
      myTestClassesFilter = filter;
    }
    else {
      String patterns = System.getProperty(TARGET_TEST_PATTERNS);
      if (patterns != null) {
        myTestClassesFilter = new PatternListTestClassFilter(StringUtil.split(patterns, ";"));
        System.out.println("Using patterns: [" + patterns +"]");
      }
      else {
        myTestClassesFilter = TestClassesFilter.ALL_CLASSES;
        System.out.println("Using all classes");
      }
    }
  }

  /*
   * Adds <code>testCaseClass</code> to the list of classes
   * if the class is a test case we wish to load. Calls
   * <code>shouldLoadTestCase ()</code> to determine that.
   */
  void addClassIfTestCase(Class testCaseClass) {
    if (shouldAddTestCase(testCaseClass, true) &&
        testCaseClass != myFirstTestClass && testCaseClass != myLastTestClass &&
        PlatformTestUtil.canRunTest(testCaseClass)) {
      myClassList.add(testCaseClass);
    }
  }

  void addFirstTest(Class aClass) {
    assert myFirstTestClass == null : "already added: "+aClass;
    assert shouldAddTestCase(aClass, false) : "not a test: "+aClass;
    myFirstTestClass = aClass;
  }

  void addLastTest(Class aClass) {
    assert myLastTestClass == null : "already added: "+aClass;
    assert shouldAddTestCase(aClass, false) : "not a test: "+aClass;
    myLastTestClass = aClass;
  }

  /**
   * Determine if we should load this test case.
   */
  private boolean shouldAddTestCase(final Class<?> testCaseClass, boolean testForExcluded) {
    if ((testCaseClass.getModifiers() & Modifier.ABSTRACT) != 0) return false;
    if (testForExcluded && shouldExcludeTestClass(testCaseClass)) return false;

    if (TestCase.class.isAssignableFrom(testCaseClass) || TestSuite.class.isAssignableFrom(testCaseClass)) {
      return true;
    }
    try {
      final Method suiteMethod = testCaseClass.getMethod("suite");
      if (Test.class.isAssignableFrom(suiteMethod.getReturnType()) && (suiteMethod.getModifiers() & Modifier.STATIC) != 0) {
        //System.out.println("testCaseClass = " + testCaseClass);
        return true;
      }
    }
    catch (NoSuchMethodException e) {
      // can't be
    }

    return TestRunnerUtil.isJUnit4TestClass(testCaseClass);
  }

  /*
   * Determine if we should exclude this test case.
   */
  private boolean shouldExcludeTestClass(Class testCaseClass) {
    String className = testCaseClass.getName();
    if (className.toLowerCase().contains("performance") && !myIsPerformanceTestsRun) return true;

    return !myTestClassesFilter.matches(className) || isBombed(testCaseClass);
  }

  public static boolean isBombed(final Method method) {
    final Bombed bombedAnnotation = method.getAnnotation(Bombed.class);
    if (bombedAnnotation == null) return false;
    if (PlatformTestUtil.isRotten(bombedAnnotation)) {
      String message = "Disarm the stale bomb for '" + method + "' in class '" + method.getDeclaringClass() + "'";
      System.err.println(message);
      //Assert.fail(message);
    }
    return !PlatformTestUtil.bombExplodes(bombedAnnotation);
  }

  public static boolean isBombed(final Class<?> testCaseClass) {
    final Bombed bombedAnnotation = testCaseClass.getAnnotation(Bombed.class);
    if (bombedAnnotation == null) return false;
    if (PlatformTestUtil.isRotten(bombedAnnotation)) {
      String message = "Disarm the stale bomb for '" + testCaseClass + "'";
      System.err.println(message);
     // Assert.fail(message);
    }
    return !PlatformTestUtil.bombExplodes(bombedAnnotation);
  }

  public void loadTestCases(final Collection<String> classNamesIterator) {
    for (String className : classNamesIterator) {
      try {
        Class candidateClass = Class.forName(className);
        addClassIfTestCase(candidateClass);
      }
      catch (ClassNotFoundException e) {
        System.err.println("Cannot load class " + className + ": " + e.getMessage());
        e.printStackTrace();
      }
      catch (ExceptionInInitializerError e) {
        System.err.println("Cannot initialize class " + className + ": " + e.getException().getMessage());
        e.printStackTrace();
        System.err.println("Root cause:");
        e.getException().printStackTrace();
      }
      catch (LinkageError e) {
        System.err.println("Cannot load class " + className + ": " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  private static final List<String> ourRankList = getTeamCityRankList();

  private static List<String> getTeamCityRankList() {
    final String filePath = System.getProperty("teamcity.tests.recentlyFailedTests.file", null);
    if (filePath == null) {
      return Collections.emptyList();
    }

    try {
      final List<String> result = new ArrayList<String>();
      final BufferedReader reader = new BufferedReader(new FileReader(filePath));
      try {
        do {
          final String className = reader.readLine();
          if (className == null) break;
          result.add(className);
        }
        while (true);
        return result;
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
  }

  private int getRank(Class aClass) {
    final String name = aClass.getName();
    if (aClass == myFirstTestClass) return -1;
    if (aClass == myLastTestClass) return myClassList.size() + ourRankList.size();
    int i = ourRankList.indexOf(name);
    if (i != -1) {
      return i;
    }
    return ourRankList.size();
  }

  public List<Class> getClasses() {
    List<Class> result = new ArrayList<Class>(myClassList.size());
    if (myFirstTestClass != null) {
      result.add(myFirstTestClass);
    }
    result.addAll(myClassList);
    if (myLastTestClass != null) {
      result.add(myLastTestClass);
    }

    if (!ourRankList.isEmpty()) {
      Collections.sort(result, new Comparator<Class>() {
        @Override
        public int compare(final Class o1, final Class o2) {
          return getRank(o1) - getRank(o2);
        }
      });
    }

    return result;
  }

  public void clearClasses() {
    myClassList.clear();
  }
}
