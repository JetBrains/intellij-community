// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.idea.Bombed;
import com.intellij.idea.ExcludeFromTestDiscovery;
import com.intellij.idea.HardwareAgentRequired;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.RunFirst;
import com.intellij.testFramework.SelfSeedingTestCase;
import com.intellij.testFramework.TestFrameworkUtil;
import com.intellij.testFramework.TestSorter;
import com.intellij.util.MathUtil;
import com.intellij.util.SystemProperties;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.ToIntFunction;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace", "TestOnlyProblems"})
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

  /**
   * By default, test classes run in alphabetical order. Pass {@code "reversed"} to this property to run test classes in reversed alphabetical order.
   * This help to find problems when test A modifies global state causing test B to fail if it runs after A.
   */
  private static final boolean REVERSE_ORDER = SystemProperties.getBooleanProperty("intellij.build.test.reverse.order", false);

  private static final String PLATFORM_LITE_FIXTURE_NAME = "com.intellij.testFramework.PlatformLiteFixture";

  private final List<Class<?>> myClassList = new ArrayList<>();
  private final List<Throwable> myClassLoadingErrors = new ArrayList<>();
  private Class<?> myFirstTestClass;
  private Class<?> myLastTestClass;
  private final TestClassesFilter myTestClassesFilter;
  private final boolean myForceLoadPerformanceTests;

  public TestCaseLoader(String classFilterName) {
    this(classFilterName, false);
  }

  public TestCaseLoader(String classFilterName, boolean forceLoadPerformanceTests) {
    myForceLoadPerformanceTests = forceLoadPerformanceTests;

    TestClassesFilter testClassesFilter = calcTestClassFilter(classFilterName);
    TestClassesFilter affectedTestsFilter = affectedTestsFilter();
    if (affectedTestsFilter != null) {
      testClassesFilter = new TestClassesFilter.And(testClassesFilter, affectedTestsFilter);
    }
    myTestClassesFilter = testClassesFilter;
    System.out.println(myTestClassesFilter);
  }

  private static TestClassesFilter calcTestClassFilter(String classFilterName) {
    String patterns = getTestPatterns();
    if (!StringUtil.isEmpty(patterns)) {
      System.out.println("Using patterns: [" + patterns + "]");
      return new PatternListTestClassFilter(StringUtil.split(patterns, ";"));
    }

    List<String> testGroupNames = getTestGroups();
    if (testGroupNames.contains(ALL_TESTS_GROUP)) {
      System.out.println("Using all classes");
      return TestClassesFilter.ALL_CLASSES;
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

    System.out.println("Loading test groups from: " + groupingFileUrls);
    MultiMap<String, String> groups = MultiMap.createLinked();
    for (URL fileUrl : groupingFileUrls) {
      try (InputStreamReader reader = new InputStreamReader(fileUrl.openStream(), StandardCharsets.UTF_8)) {
        GroupBasedTestClassFilter.readGroups(reader, groups);
      }
      catch (IOException e) {
        System.err.println("Failed to load test groups from " + fileUrl);
        e.printStackTrace();
      }
    }
    System.out.println("Using test groups: " + testGroupNames);
    Set<String> testGroupNameSet = new HashSet<>(testGroupNames);
    testGroupNameSet.removeAll(groups.keySet());
    if (!testGroupNameSet.isEmpty()) {
      System.err.println("Unknown test groups: " + testGroupNameSet);
    }
    return new GroupBasedTestClassFilter(groups, testGroupNames);
  }

  private static @Nullable String getTestPatterns() {
    return System.getProperty("intellij.build.test.patterns", System.getProperty("idea.test.patterns"));
  }

  private static @Nullable TestClassesFilter affectedTestsFilter() {
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
    System.out.println("No affected tests were found, will run with the standard test filter");
    return null;
  }

  private static List<String> getTestGroups() {
    return StringUtil.split(System.getProperty("intellij.build.test.groups", System.getProperty("idea.test.group", "")).trim(), ";");
  }

  void addClassIfTestCase(Class<?> testCaseClass, String moduleName) {
    if (shouldAddTestCase(testCaseClass, moduleName, true) &&
        testCaseClass != myFirstTestClass && testCaseClass != myLastTestClass &&
        TestFrameworkUtil.canRunTest(testCaseClass)) {

      if (SelfSeedingTestCase.class.isAssignableFrom(testCaseClass) || matchesCurrentBucket(testCaseClass.getName())) {
        myClassList.add(testCaseClass);
      }
    }
  }

  /**
   * @return true iff this {@code testIdentifier} matches current testing settings: number of buckets and bucket index. {@code testIdentifier} may
   * be something identifying a test: test class or feature file name
   * @apiNote logic for bucketing tests into different bucket configurations.
   * @see TestCaseLoader#TEST_RUNNERS_COUNT
   * @see TestCaseLoader#TEST_RUNNER_INDEX
   */
  public static boolean matchesCurrentBucket(@NotNull String testIdentifier) {
    return MathUtil.nonNegativeAbs(testIdentifier.hashCode()) % TEST_RUNNERS_COUNT == TEST_RUNNER_INDEX;
  }

  /**
   * @return true iff tests supposed to be separated into buckets using {@link #matchesCurrentBucket(String)} method
   */
  public static boolean shouldBucketTests() {
    return TEST_RUNNERS_COUNT > 1;
  }

  void addFirstTest(Class<?> aClass) {
    assert myFirstTestClass == null : "already added: " + aClass;
    assert shouldAddTestCase(aClass, null, false) : "not a test: " + aClass;
    myFirstTestClass = aClass;
  }

  void addLastTest(Class<?> aClass) {
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

  private boolean shouldExcludeTestClass(String moduleName, Class<?> testCaseClass) {
    if (!myForceLoadPerformanceTests && !shouldIncludePerformanceTestCase(testCaseClass.getSimpleName())) return true;
    String className = testCaseClass.getName();
    return !myTestClassesFilter.matches(className, moduleName) || isBombed(testCaseClass) || isExcludeFromTestDiscovery(testCaseClass);
  }

  private static boolean isExcludeFromTestDiscovery(Class<?> c) {
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
        Class<?> candidateClass = Class.forName(className, false, getClassLoader());
        addClassIfTestCase(candidateClass, moduleName);
      }
      catch (Throwable e) {
        String message = "Cannot load class " + className + ": " + e.getMessage();
        System.err.println(message);
        myClassLoadingErrors.add(new Throwable(message, e));
      }
    }
  }

  protected static ClassLoader getClassLoader() {
    return TestCaseLoader.class.getClassLoader();
  }

  public List<Throwable> getClassLoadingErrors() {
    return myClassLoadingErrors;
  }

  private static int getRank(Class<?> aClass) {
    if (runFirst(aClass)) return 0;

    // PlatformLiteFixture is the very special test case because it doesn't load all the XMLs with component/extension declarations
    // (that is, uses a mock application). Instead, it allows to declare them manually using its registerComponent/registerExtension
    // methods. The goal is to make tests which extend PlatformLiteFixture extremely fast. The problem appears when such tests are invoked
    // together with other tests which rely on declarations in XML files (that is, use a real application). The nature of the IDE
    // application is such that static final fields are often used to cache extensions. While having a positive effect on performance,
    // it creates problems during testing. Simply speaking, if the instance of PlatformLiteFixture is the first one in a suite, it pollutes
    // static final fields (and all other kinds of caches) with invalid values. To avoid it, such tests should always be the last.
    if (isPlatformLiteFixture(aClass)) {
      return 2;
    }

    return 1;
  }

  private static boolean runFirst(Class<?> testClass) {
    return getAnnotationInHierarchy(testClass, RunFirst.class) != null;
  }

  private static boolean isPlatformLiteFixture(Class<?> aClass) {
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

  public List<Class<?>> getClasses() {
    List<Class<?>> result = new ArrayList<>(myClassList.size());

    if (myFirstTestClass != null) {
      result.add(myFirstTestClass);
    }

    result.addAll(loadTestSorter().sorted(myClassList, TestCaseLoader::getRank));

    if (myLastTestClass != null) {
      result.add(myLastTestClass);
    }

    return result;
  }

  private static TestSorter loadTestSorter() {
    String sorter = System.getProperty("intellij.build.test.sorter");
    if (sorter != null) {
      try {
        return (TestSorter)Class.forName(sorter).getConstructor().newInstance();
      }
      catch (Throwable t) {
        System.err.println("Sorter initialization failed: " + sorter);
        t.printStackTrace();
      }
    }

    Comparator<String> classNameComparator = REVERSE_ORDER ? Comparator.reverseOrder() : Comparator.naturalOrder();
    return new TestSorter() {
      @Override
      public @NotNull List<Class<?>> sorted(@NotNull List<Class<?>> tests, @NotNull ToIntFunction<? super Class<?>> ranker) {
        return ContainerUtil.sorted(tests, Comparator.<Class<?>>comparingInt(ranker).thenComparing(Class::getName, classNameComparator));
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

  public static boolean shouldIncludePerformanceTestCase(String className) {
    return isIncludingPerformanceTestsRun() || isPerformanceTestsRun() || !isPerformanceTest(null, className);
  }

  static boolean isPerformanceTest(String methodName, String className) {
    return TestFrameworkUtil.isPerformanceTest(methodName, className);
  }

  private static TestClassesFilter ourFilter;
  public static boolean isClassIncluded(String className) {
    if (!INCLUDE_UNCONVENTIONALLY_NAMED_TESTS && 
        !className.endsWith("Test")) {
      return false;
    }

    if (ourFilter == null) {
      ourFilter = calcTestClassFilter("tests/testGroups.properties");
    }
    return shouldIncludePerformanceTestCase(className) &&
           matchesCurrentBucket(className) &&
           ourFilter.matches(className);
  }
  
  public void fillTestCases(String rootPackage, List<Path> classesRoots) {
    long t = System.nanoTime();

    for (Path classesRoot : classesRoots) {
      int count = getClassesCount();
      ClassFinder classFinder = new ClassFinder(classesRoot.toFile(), rootPackage, INCLUDE_UNCONVENTIONALLY_NAMED_TESTS);
      loadTestCases(classesRoot.getFileName().toString(), classFinder.getClasses());
      count = getClassesCount() - count;
      if (count > 0) {
        System.out.println("Loaded " + count + " classes from class root " + classesRoot);
      }
    }

    if (myClassList.isEmpty()) { // nothing valuable to test
      clearClasses();
    }

    t = (System.nanoTime() - t) / 1_000_000;
    System.out.println("Loaded " + getClassesCount() + " classes in " + t + " ms");

    if (!RUN_ONLY_AFFECTED_TESTS && getClassesCount() == 0 && !Boolean.getBoolean("idea.tests.ignoreJUnit3EmptySuite")) {
      // Specially formatted error message will fail the build
      // See https://www.jetbrains.com/help/teamcity/build-script-interaction-with-teamcity.html#BuildScriptInteractionwithTeamCity-ReportingMessagesForBuildLog
      System.out.println("##teamcity[message text='Expected some junit 3 or junit 4 tests to be executed, but no test classes were found. " +
                         "If all your tests are junit 5 and you do not expect old junit tests to be executed, please pass vm option " +
                         "-Didea.tests.ignoreJUnit3EmptySuite=true' status='ERROR']");
    }
  }

  public static @Nullable <T extends Annotation> T getAnnotationInHierarchy(@NotNull Class<?> clazz, @NotNull Class<T> annotationClass) {
    Class<?> current = clazz;
    while (current != null) {
      T annotation = current.getAnnotation(annotationClass);
      if (annotation != null) return annotation;
      current = current.getSuperclass();
    }
    return null;
  }
}
