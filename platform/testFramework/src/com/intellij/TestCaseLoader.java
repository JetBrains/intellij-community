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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.util.containers.MultiMap;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral", "UseOfSystemOutOrSystemErr", "CallToPrintStackTrace", "TestOnlyProblems"})
public class TestCaseLoader {
  public static final String TARGET_TEST_GROUP = "idea.test.group";
  public static final String TARGET_TEST_PATTERNS = "idea.test.patterns";
  public static final String PERFORMANCE_TESTS_ONLY_FLAG = "idea.performance.tests";
  public static final String INCLUDE_PERFORMANCE_TESTS_FLAG = "idea.include.performance.tests";
  public static final String INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG = "idea.include.unconventionally.named.tests";
  public static final String SKIP_COMMUNITY_TESTS = "idea.skip.community.tests";

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
    String patterns = System.getProperty(TARGET_TEST_PATTERNS);
    if (!StringUtil.isEmpty(patterns)) {
      myTestClassesFilter = new PatternListTestClassFilter(StringUtil.split(patterns, ";"));
      System.out.println("Using patterns: [" + patterns +"]");
    }
    else {
      List<URL> groupingFileUrls = Collections.emptyList();
      if (!StringUtil.isEmpty(classFilterName)) {
        try {
          groupingFileUrls = Collections.list(getClass().getClassLoader().getResources(classFilterName));
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }

      List<String> testGroupNames = StringUtil.split(System.getProperty(TARGET_TEST_GROUP, "").trim(), ";");
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

      if (groups.isEmpty()) {
        System.out.println("Using all classes");
        myTestClassesFilter = TestClassesFilter.ALL_CLASSES;
      }
      else {
        System.out.println("Using test groups: " + testGroupNames);
        myTestClassesFilter = new GroupBasedTestClassFilter(groups, testGroupNames);
      }
    }
  }

  void addClassIfTestCase(Class testCaseClass, String moduleName) {
    if (shouldAddTestCase(testCaseClass, moduleName, true) &&
        testCaseClass != myFirstTestClass && testCaseClass != myLastTestClass &&
        PlatformTestUtil.canRunTest(testCaseClass)) {
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

    return TestRunnerUtil.isJUnit4TestClass(testCaseClass);
  }

  private boolean shouldExcludeTestClass(String moduleName, Class testCaseClass) {
    if (!myForceLoadPerformanceTests && !TestAll.shouldIncludePerformanceTestCase(testCaseClass)) return true;
    String className = testCaseClass.getName();

    return !myTestClassesFilter.matches(className, moduleName) || isBombed(testCaseClass);
  }

  public static boolean isBombed(final AnnotatedElement element) {
    final Bombed bombedAnnotation = element.getAnnotation(Bombed.class);
    if (bombedAnnotation == null) return false;
    return !PlatformTestUtil.bombExplodes(bombedAnnotation);
  }
  
  public void loadTestCases(final String moduleName, final Collection<String> classNamesIterator) {
    for (String className : classNamesIterator) {
      try {
        Class candidateClass = Class.forName(className, false, getClass().getClassLoader());
        addClassIfTestCase(candidateClass, moduleName);
      }
      catch (Throwable e) {
        String message = "Cannot load class " + className + ": " + e.getMessage();
        System.err.println(message);
        myClassLoadingErrors.add(new Throwable(message, e));
      }
    }
  }

  public List<Throwable> getClassLoadingErrors() {
    return myClassLoadingErrors;
  }

  private static final List<String> ourRankList = getTeamCityRankList();

  private static List<String> getTeamCityRankList() {
    String filePath = System.getProperty("teamcity.tests.recentlyFailedTests.file", null);
    if (filePath != null) {
      try {
        return FileUtil.loadLines(filePath);
      }
      catch (IOException ignored) { }
    }

    return Collections.emptyList();
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
    List<Class> result = new ArrayList<>(myClassList.size());
    if (myFirstTestClass != null) {
      result.add(myFirstTestClass);
    }
    result.addAll(myClassList);
    if (myLastTestClass != null) {
      result.add(myLastTestClass);
    }

    if (!ourRankList.isEmpty()) {
      Collections.sort(result, (o1, o2) -> getRank(o1) - getRank(o2));
    }

    return result;
  }

  public void clearClasses() {
    myClassList.clear();
  }
}
