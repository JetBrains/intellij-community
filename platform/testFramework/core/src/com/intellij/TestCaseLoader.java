// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.idea.Bombed;
import com.intellij.idea.ExcludeFromTestDiscovery;
import com.intellij.idea.HardwareAgentRequired;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.RunFirst;
import com.intellij.testFramework.TeamCityLogger;
import com.intellij.testFramework.TestFrameworkUtil;
import com.intellij.testFramework.TestSorter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.function.ToIntFunction;

@SuppressWarnings({"HardCodedStringLiteral", "UseOfSystemOutOrSystemErr", "CallToPrintStackTrace", "TestOnlyProblems"})
public class TestCaseLoader {
  public static final String PERFORMANCE_TESTS_ONLY_FLAG = "idea.performance.tests";
  public static final String INCLUDE_PERFORMANCE_TESTS_FLAG = "idea.include.performance.tests";
  public static final String INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG = "idea.include.unconventionally.named.tests";
  public static final String RUN_ONLY_AFFECTED_TEST_FLAG = "idea.run.only.affected.tests";
  public static final String TEST_RUNNERS_COUNT_FLAG = "idea.test.runners.count";
  public static final String TEST_RUNNER_INDEX_FLAG = "idea.test.runner.index";
  public static final String HARDWARE_AGENT_REQUIRED_FLAG = "idea.hardware.agent.required";

  private static final boolean PERFORMANCE_TESTS_ONLY = "true".equals(System.getProperty(PERFORMANCE_TESTS_ONLY_FLAG));
  private static final boolean INCLUDE_PERFORMANCE_TESTS = "true".equals(System.getProperty(INCLUDE_PERFORMANCE_TESTS_FLAG));
  private static final boolean INCLUDE_UNCONVENTIONALLY_NAMED_TESTS = "true".equals(System.getProperty(INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG));
  private static final boolean RUN_ONLY_AFFECTED_TESTS = "true".equals(System.getProperty(RUN_ONLY_AFFECTED_TEST_FLAG));
  private static final boolean RUN_WITH_TEST_DISCOVERY = System.getProperty("test.discovery.listener") != null;
  private static final boolean HARDWARE_AGENT_REQUIRED = "true".equals(System.getProperty(HARDWARE_AGENT_REQUIRED_FLAG));

  private static final int TEST_RUNNERS_COUNT = Integer.valueOf(System.getProperty(TEST_RUNNERS_COUNT_FLAG, "1"));
  private static final int TEST_RUNNER_INDEX = Integer.valueOf(System.getProperty(TEST_RUNNER_INDEX_FLAG, "0"));

  /**
   * An implicit group which includes all tests from all defined groups and tests which don't belong to any group.
   */
  private static final String ALL_TESTS_GROUP = "ALL";
  private static final String PLATFORM_LITE_FIXTURE_NAME = "com.intellij.testFramework.PlatformLiteFixture";

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
    TestClassesFilter testClassesFilter = calcTestClassFilter(classFilterName);
    TestClassesFilter affectedTestsFilter = affectedTestsFilter();

    myTestClassesFilter = new TestClassesFilter.And(testClassesFilter, affectedTestsFilter);
    System.out.println(myTestClassesFilter.toString());
  }

  private TestClassesFilter calcTestClassFilter(String classFilterName) {
    String patterns = getTestPatterns();
    if (!StringUtil.isEmpty(patterns)) {
      System.out.println("Using patterns: [" + patterns + "]");
      return new PatternListTestClassFilter(StringUtil.split(patterns, ";"));
    }
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
      try (InputStreamReader reader = new InputStreamReader(fileUrl.openStream())) {
        groups.putAllValues(GroupBasedTestClassFilter.readGroups(reader));
      }
      catch (IOException e) {
        e.printStackTrace();
        System.err.println("Failed to load test groups from " + fileUrl);
      }
    }

    if (groups.isEmpty() || testGroupNames.contains(ALL_TESTS_GROUP)) {
      System.out.println("Using all classes");
      return TestClassesFilter.ALL_CLASSES;
    }
    System.out.println("Using test groups: " + testGroupNames);
    return new GroupBasedTestClassFilter(groups, testGroupNames);
  }

  @Nullable
  private static String getTestPatterns() {
    return System.getProperty("intellij.build.test.patterns", System.getProperty("idea.test.patterns"));
  }

  @NotNull
  private static TestClassesFilter affectedTestsFilter() {
    if (RUN_ONLY_AFFECTED_TESTS) {
      System.out.println("Trying to load affected tests.");
      File affectedTestClasses = new File(System.getProperty("idea.home.path"), "discoveredTestClasses.txt");
      if (affectedTestClasses.exists()) {
        System.out.println("Loading file with affected classes " + affectedTestClasses.getAbsolutePath());
        try {
          return new PatternListTestClassFilter(FileUtil.loadLines(affectedTestClasses));
        }
        catch (IOException e) {
          e.printStackTrace();
          throw new RuntimeException(e);
        }
      }
    }
    System.out.println("No affected tests were found, will run with the standard test filter.");
    return TestClassesFilter.ALL_CLASSES;
  }

  @NotNull
  private static List<String> getTestGroups() {
    return StringUtil.split(System.getProperty("intellij.build.test.groups", System.getProperty("idea.test.group", "")).trim(), ";");
  }

  void addClassIfTestCase(Class testCaseClass, String moduleName) {
    if (shouldAddTestCase(testCaseClass, moduleName, true) &&
        testCaseClass != myFirstTestClass && testCaseClass != myLastTestClass &&
        TestFrameworkUtil.canRunTest(testCaseClass)) {

      int index = Math.abs(testCaseClass.getName().hashCode());

      if (index % TEST_RUNNERS_COUNT == TEST_RUNNER_INDEX) {
        myClassList.add(testCaseClass);
      }
    }
  }

  void addFirstTest(Class aClass) {
    assert myFirstTestClass == null : "already added: " + aClass;
    assert shouldAddTestCase(aClass, null, false) : "not a test: " + aClass;
    myFirstTestClass = aClass;
  }

  void addLastTest(Class aClass) {
    assert myLastTestClass == null : "already added: " + aClass;
    assert shouldAddTestCase(aClass, null, false) : "not a test: " + aClass;
    myLastTestClass = aClass;
  }

  private boolean shouldAddTestCase(Class<?> testCaseClass, String moduleName, boolean checkForExclusion) {
    if ((testCaseClass.getModifiers() & Modifier.ABSTRACT) != 0) return false;

    if (checkForExclusion) {
      if (shouldExcludeTestClass(moduleName, testCaseClass)) return false;

      boolean isHardwareAgentRequired = getAnnotationInHierarchy(testCaseClass, HardwareAgentRequired.class) != null;
      if (isHardwareAgentRequired != HARDWARE_AGENT_REQUIRED) return false;
    }

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

    return TestFrameworkUtil.isJUnit4TestClass(testCaseClass, false);
  }

  private boolean shouldExcludeTestClass(String moduleName, Class testCaseClass) {
    if (!myForceLoadPerformanceTests && !shouldIncludePerformanceTestCase(testCaseClass)) return true;
    String className = testCaseClass.getName();
    return !myTestClassesFilter.matches(className, moduleName) || isBombed(testCaseClass) || isExcludeFromTestDiscovery(testCaseClass);
  }

  private static boolean isExcludeFromTestDiscovery(Class c) {
    return RUN_WITH_TEST_DISCOVERY && getAnnotationInHierarchy(c, ExcludeFromTestDiscovery.class) != null;
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
    if (runFirst(aClass)) return 0;
    if (isPerformanceTestsRun()) return 1;

    // PlatformLiteFixture is the very special test case because it doesn't load all the XMLs with component/extension declarations
    // (that is, uses a mock application). Instead, it allows to declare them manually using its registerComponent/registerExtension
    // methods. The goal is to make tests which extend PlatformLiteFixture extremely fast. The problem appears when such tests are invoked
    // together with other tests which rely on declarations in XML files (that is, use a real application). The nature of the IDEA
    // application is such that static final fields are often used to cache extensions. While having a positive effect on performance,
    // it creates problems during testing. Simply speaking, if the instance of PlatformLiteFixture is the first one in a suite, it pollutes
    // static final fields (and all other kinds of caches) with invalid values. To avoid it, such tests should always be the last.
    if (isPlatformLiteFixture(aClass)) {
      return ourRankList.size() + 1;
    }

    int i = ourRankList.indexOf(aClass.getName());
    if (i != -1) {
      return i;
    }
    return ourRankList.size();
  }

  private static boolean runFirst(Class testClass) {
    return getAnnotationInHierarchy(testClass, RunFirst.class) != null;
  }

  private static boolean isPlatformLiteFixture(Class aClass) {
    while (aClass != null) {
      if (PLATFORM_LITE_FIXTURE_NAME.equals(aClass.getName())) {
        return true;
      }
      else {
        aClass = aClass.getSuperclass();
      }
    }
    return false;
  }

  public int getClassesCount() {
    return myClassList.size();
  }

  public List<Class> getClasses() {
    List<Class> result = new ArrayList<>(myClassList.size());

    if (myFirstTestClass != null) {
      result.add(myFirstTestClass);
    }

    result.addAll(loadTestSorter().sorted(myClassList, TestCaseLoader::getRank));

    if (myLastTestClass != null) {
      result.add(myLastTestClass);
    }

    return result;
  }

  @NotNull
  private static TestSorter loadTestSorter() {
    final String sorter = System.getProperty("intellij.build.test.sorter");
    if (sorter != null) {
      try {
        return (TestSorter)Class.forName(sorter).newInstance();
      }
      catch (Throwable ignore) { }
    }

    return new TestSorter() {
      @NotNull
      @Override
      public List<Class> sorted(@NotNull List<Class> tests, @NotNull ToIntFunction<? super Class> ranker) {
        return ContainerUtil.sorted(tests, Comparator.comparingInt(ranker));
      }
    };
  }

  private void clearClasses() {
    myClassList.clear();
    myFirstTestClass = null;
    myLastTestClass = null;
  }

  static boolean isPerformanceTestsRun() {
    return PERFORMANCE_TESTS_ONLY;
  }

  static boolean isIncludingPerformanceTestsRun() {
    return INCLUDE_PERFORMANCE_TESTS;
  }

  static boolean shouldIncludePerformanceTestCase(Class aClass) {
    return isIncludingPerformanceTestsRun() || isPerformanceTestsRun() || !isPerformanceTest(null, aClass);
  }

  static boolean isPerformanceTest(String methodName, Class aClass) {
    return TestFrameworkUtil.isPerformanceTest(methodName, aClass.getSimpleName());
  }

  public void fillTestCases(String rootPackage, List<File> classesRoots) {
    long before = System.currentTimeMillis();
    for (File classesRoot : classesRoots) {
      int oldCount = getClassesCount();
      ClassFinder classFinder = new ClassFinder(classesRoot, rootPackage, INCLUDE_UNCONVENTIONALLY_NAMED_TESTS);
      loadTestCases(classesRoot.getName(), classFinder.getClasses());
      int newCount = getClassesCount();
      if (newCount != oldCount) {
        System.out.println("Loaded " + (newCount - oldCount) + " tests from class root " + classesRoot);
      }
    }

    if (myClassList.isEmpty()) { // nothing valuable to test
      clearClasses();
    }
    long after = System.currentTimeMillis();

    String message = "Number of test classes found: " + getClassesCount() + " time to load: " + (after - before) / 1000 + "s.";
    System.out.println(message);
    TeamCityLogger.info(message);
  }

  @Nullable
  public static <T extends Annotation> T getAnnotationInHierarchy(@NotNull Class<?> clazz, @NotNull Class<T> annotationClass) {
    Class<?> current = clazz;
    while (current != null) {
      T annotation = current.getAnnotation(annotationClass);
      if (annotation != null) {
        return annotation;
      }
      current = current.getSuperclass();
    }
    return null;
  }
}