// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.idea.Bombed;
import com.intellij.idea.ExcludeFromTestDiscovery;
import com.intellij.idea.HardwareAgentRequired;
import com.intellij.nastradamus.NastradamusClient;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.teamcity.TeamCityClient;
import com.intellij.testFramework.*;
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
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
  public static final String VERBOSE_LOG_ENABLED_FLAG = "idea.test.log.verbose";
  public static final String FAIR_BUCKETING_FLAG = "idea.fair.bucketing";
  public static final String NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED_FLAG = "idea.enable.nastradamus.test.distributor";
  public static final String NASTRADAMUS_SHADOW_DATA_COLLECTION_ENABLED_FLAG = "idea.nastradamus.shadow.data.collection.enabled";

  private static final boolean PERFORMANCE_TESTS_ONLY = Boolean.getBoolean(PERFORMANCE_TESTS_ONLY_FLAG);
  private static final boolean INCLUDE_PERFORMANCE_TESTS = Boolean.getBoolean(INCLUDE_PERFORMANCE_TESTS_FLAG);
  private static final boolean INCLUDE_UNCONVENTIONALLY_NAMED_TESTS = Boolean.getBoolean(INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG);
  private static final boolean RUN_ONLY_AFFECTED_TESTS = Boolean.getBoolean(RUN_ONLY_AFFECTED_TEST_FLAG);
  private static final boolean RUN_WITH_TEST_DISCOVERY = System.getProperty("test.discovery.listener") != null;
  private static final boolean HARDWARE_AGENT_REQUIRED = Boolean.getBoolean(HARDWARE_AGENT_REQUIRED_FLAG);
  public static final boolean IS_VERBOSE_LOG_ENABLED = Boolean.getBoolean(VERBOSE_LOG_ENABLED_FLAG);

  public static final int TEST_RUNNERS_COUNT = Integer.parseInt(System.getProperty(TEST_RUNNERS_COUNT_FLAG, "1"));
  public static final int TEST_RUNNER_INDEX = Integer.parseInt(System.getProperty(TEST_RUNNER_INDEX_FLAG, "0"));

  private static final AtomicInteger CYCLIC_BUCKET_COUNTER = new AtomicInteger(0);
  private static final HashMap<String, Integer> BUCKETS = new HashMap<>();
  /**
   * Distribute tests equally among the buckets
   */
  private static final boolean IS_FAIR_BUCKETING = Boolean.getBoolean(FAIR_BUCKETING_FLAG);

  /**
   * Intelligent test distribution to shorten time of tests run (ultimately - predict what tests to run on a changeset)
   */
  public static final boolean IS_NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED = Boolean.getBoolean(NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED_FLAG);

  /**
   * During the usual runs of the "old" aggregator the data about the run will be sent to Nastradamus service
   */
  public static final boolean IS_NASTRADAMUS_SHADOW_DATA_COLLECTION_ENABLED =
    Boolean.getBoolean(NASTRADAMUS_SHADOW_DATA_COLLECTION_ENABLED_FLAG);

  /**
   * An implicit group which includes all tests from all defined groups and tests which don't belong to any group.
   */
  private static final String ALL_TESTS_GROUP = "ALL";

  /**
   * By default, test classes run in alphabetical order.
   * Pass {@code "reversed"} to this property to run test classes in reversed alphabetical order.
   * This helps to find problems when test A modifies the global state, causing test B to fail if it runs after A.
   */
  private static final boolean REVERSE_ORDER = SystemProperties.getBooleanProperty("intellij.build.test.reverse.order", false);

  private static final String PLATFORM_LITE_FIXTURE_NAME = "com.intellij.testFramework.PlatformLiteFixture";

  private final HashSet<Class<?>> myClassSet = new HashSet<>();
  private final List<Throwable> myClassLoadingErrors = new ArrayList<>();
  private Class<?> myFirstTestClass;
  private Class<?> myLastTestClass;
  private final TestClassesFilter myTestClassesFilter;
  private final boolean myForceLoadPerformanceTests;

  private static final AtomicBoolean isNastradamusCacheInitialized = new AtomicBoolean();

  private static final NastradamusClient nastradamusClient = initNastradamus();

  static {
    initFairBuckets(false);
  }

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

    List<URL> finalGroupingFileUrls = groupingFileUrls;
    TeamCityLogger.block("Loading test groups from ...", () -> {
      System.out.println("Loading test groups from: " + finalGroupingFileUrls);
    });
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
    else {
      System.out.println("Affected tests discovery is disabled. Will run with the standard test filter");
    }
    return null;
  }

  private static List<String> getTestGroups() {
    return StringUtil.split(System.getProperty("intellij.build.test.groups", System.getProperty("idea.test.group", "")).trim(), ";");
  }

  private boolean isClassTestCase(Class<?> testCaseClass, String moduleName) {
    if (shouldAddTestCase(testCaseClass, moduleName, true) &&
        testCaseClass != myFirstTestClass &&
        testCaseClass != myLastTestClass &&
        TestFrameworkUtil.canRunTest(testCaseClass)) {

      // fair bucketing initialization
      if (IS_FAIR_BUCKETING && BUCKETS.isEmpty()) return true;

      // warmup caches from nastradamus (if it's not fully initialized)
      if ((IS_NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED || IS_NASTRADAMUS_SHADOW_DATA_COLLECTION_ENABLED)
          && !isNastradamusCacheInitialized.get()) {
        return true;
      }

      if (SelfSeedingTestCase.class.isAssignableFrom(testCaseClass) || matchesCurrentBucket(testCaseClass.getName())) {
        return true;
      }
    }

    return false;
  }

  private static boolean matchesBucketViaNastradamus(@NotNull String testIdentifier) {
    try {
      return nastradamusClient.isClassInBucket(testIdentifier, (testClassName) -> matchesCurrentBucketViaHashing(testClassName));
    }
    catch (Exception e) {
      // if fails, just fallback to consistent hashing
      return matchesCurrentBucketViaHashing(testIdentifier);
    }
  }

  static boolean matchesCurrentBucketViaHashing(@NotNull String testIdentifier) {
    return MathUtil.nonNegativeAbs(testIdentifier.hashCode()) % TEST_RUNNERS_COUNT == TEST_RUNNER_INDEX;
  }

  /**
   * @return true iff this {@code testIdentifier} matches current testing settings: number of buckets and bucket index. {@code testIdentifier} may
   * be something identifying a test: test class or feature file name
   * @apiNote logic for bucketing tests into different bucket configurations.
   * @see TestCaseLoader#TEST_RUNNERS_COUNT
   * @see TestCaseLoader#TEST_RUNNER_INDEX
   */
  public static boolean matchesCurrentBucket(@NotNull String testIdentifier) {
    // just run aggregator "as usual", but send the data to Nastradamus
    if (IS_NASTRADAMUS_SHADOW_DATA_COLLECTION_ENABLED) {
      // do not return result until Nastradamus is "production-ready"
      matchesBucketViaNastradamus(testIdentifier);
    }

    if (IS_NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED) return matchesBucketViaNastradamus(testIdentifier);

    if (!IS_FAIR_BUCKETING) {
      return matchesCurrentBucketViaHashing(testIdentifier);
    }

    initFairBuckets(false);

    return matchesCurrentBucketFair(testIdentifier, TEST_RUNNERS_COUNT, TEST_RUNNER_INDEX);
  }

  private static List<Class<?>> loadClassesForWarmup() {
    var groupsTestCaseLoader = new TestCaseLoader("tests/testGroups.properties");

    for (Path classesRoot : TestAll.getClassRoots()) {
      ClassFinder classFinder = new ClassFinder(classesRoot.toFile(), "", INCLUDE_UNCONVENTIONALLY_NAMED_TESTS);

      Collection<String> foundTestClasses = classFinder.getClasses();
      groupsTestCaseLoader.loadTestCases(classesRoot.getFileName().toString(), foundTestClasses);
    }

    var testCaseClasses = groupsTestCaseLoader.getClasses();

    System.out.printf("Finishing warmup initialization. Found %s classes%n", testCaseClasses.size());

    if (testCaseClasses.isEmpty()) {
      System.err.println("Fair bucketing or Nastradamus is enabled, but 0 test classes were found for warmup");
    }

    return testCaseClasses;
  }

  private static synchronized NastradamusClient initNastradamus() {
    if (!(IS_NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED || IS_NASTRADAMUS_SHADOW_DATA_COLLECTION_ENABLED)) return null;

    if (isNastradamusCacheInitialized.get()) return nastradamusClient;

    var testCaseClasses = loadClassesForWarmup();
    NastradamusClient nastradamus = null;
    try {
      System.out.println("Caching data from Nastradamus and TeamCity ...");
      nastradamus = new NastradamusClient(
        new URI(System.getProperty("idea.nastradamus.url")).normalize(),
        testCaseClasses,
        new TeamCityClient()
      );
      nastradamus.getRankedClasses();
      System.out.println("Caching data from Nastradamus and TeamCity finished");
    }
    catch (Exception e) {
      System.err.println("Unexpected exception during Nastradamus client instance initialization");
      e.printStackTrace();
    }

    isNastradamusCacheInitialized.set(true);
    return nastradamus;
  }

  public static void sendTestRunResultsToNastradamus() {
    if (!(IS_NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED || IS_NASTRADAMUS_SHADOW_DATA_COLLECTION_ENABLED)) return;

    try {
      var testRunRequest = nastradamusClient.collectTestRunResults();
      nastradamusClient.sendTestRunResults(testRunRequest);
    }
    catch (Exception e) {
      System.err.println("Unexpected error happened during sending test results to Nastradamus");
      e.printStackTrace();
    }
  }

  /**
   * Init fair buckets for all test classes
   */
  public static synchronized void initFairBuckets(boolean useAsNastradamusFallback) {
    if (useAsNastradamusFallback) {
      // buckets were already initialized
      if (!BUCKETS.isEmpty()) return;
    }
    else if (!IS_FAIR_BUCKETING || !BUCKETS.isEmpty()) return;

    System.out.println("Fair bucketing initialization started ...");

    var testCaseClasses = loadClassesForWarmup();

    testCaseClasses.forEach(testCaseClass -> matchesCurrentBucketFair(testCaseClass.getName(), TEST_RUNNERS_COUNT, TEST_RUNNER_INDEX));
    System.out.println("Fair bucketing initialization finished.");
  }

  public static boolean matchesCurrentBucketFair(@NotNull String testIdentifier, int testRunnerCount, int testRunnerIndex) {
    var value = BUCKETS.get(testIdentifier);

    if (value != null) {
      var isMatchedBucket = value == testRunnerIndex;

      if (IS_VERBOSE_LOG_ENABLED) {
        System.out.printf(
          "Fair bucket match: test identifier `%s` (already sieved to buckets), runner count %s, runner index %s, is matching bucket %s%n",
          testIdentifier, testRunnerCount, testRunnerIndex, isMatchedBucket);
      }

      return isMatchedBucket;
    }
    else {
      BUCKETS.put(testIdentifier, CYCLIC_BUCKET_COUNTER.getAndIncrement());
    }

    if (CYCLIC_BUCKET_COUNTER.get() == testRunnerCount) CYCLIC_BUCKET_COUNTER.set(0);

    var isMatchedBucket = BUCKETS.get(testIdentifier) == testRunnerIndex;

    if (IS_VERBOSE_LOG_ENABLED) {
      System.out.printf("Fair bucket match: test identifier `%s`, runner count %s, runner index %s, is matching bucket %s%n",
                        testIdentifier, testRunnerCount, testRunnerIndex, isMatchedBucket);
    }

    return isMatchedBucket;
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
    catch (NoSuchMethodException ignored) {
    }

    return TestFrameworkUtil.isJUnit4TestClass(testCaseClass, false)
           || TestFrameworkUtil.isJUnit5TestClass(testCaseClass, false);
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
        if (isClassTestCase(candidateClass, moduleName)) {
          myClassSet.add(candidateClass);
        }
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

  public static int getRank(Class<?> aClass) {
    if (runFirst(aClass)) return 0;

    // `PlatformLiteFixture` is a very special test case, because it doesn't load all the XMLs with component/extension declarations
    // (that is, uses a mock application). Instead, it allows declaring them manually using its registerComponent/registerExtension
    // methods. The goal is to make tests which extend PlatformLiteFixture extremely fast. The problem appears when such tests are invoked
    // together with other tests which rely on declarations in XML files (that is, use a real application). The nature of the IDE
    // application is such that static final fields are often used to cache extensions. While having a positive effect on performance,
    // it creates problems during testing. Simply speaking, if the instance of PlatformLiteFixture is the first one in a suite, it pollutes
    // static final fields (and all other kinds of caches) with invalid values. To avoid it, such tests should always be the last.
    if (isPlatformLiteFixture(aClass)) {
      return Integer.MAX_VALUE;
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
    return myClassSet.size();
  }

  /**
   * @return Sorted list of loaded classes
   */
  public List<Class<?>> getClasses() {
    List<Class<?>> result = new ArrayList<>(myClassSet.size());

    if (myFirstTestClass != null) {
      result.add(myFirstTestClass);
    }

    result.addAll(loadTestSorter().sorted(myClassSet.stream().toList(), TestCaseLoader::getRank));

    if (myLastTestClass != null) {
      result.add(myLastTestClass);
    }

    if (IS_VERBOSE_LOG_ENABLED) {
      System.out.println("Sorted classes: ");
      result.forEach(clazz -> System.out.println(clazz.getName()));
    }
    return result;
  }

  private static TestSorter loadTestSorter() {
    String sorter = System.getProperty("intellij.build.test.sorter");

    // use Nostradamus test sorter in case, if no other is specified
    if (sorter == null && IS_NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED) {
      sorter = "com.intellij.nastradamus.NastradamusTestCaseSorter";
    }

    if (sorter != null) {
      try {
        var testSorter = (TestSorter)Class.forName(sorter).getConstructor().newInstance();
        System.out.printf("Using test sorter from %s%n", sorter);
        return testSorter;
      }
      catch (Throwable t) {
        System.err.println("Sorter initialization failed: " + sorter);
        t.printStackTrace();
      }
    }

    if (IS_VERBOSE_LOG_ENABLED) {
      System.out.println("Using default test sorter (natural order)");
    }

    Comparator<String> classNameComparator = REVERSE_ORDER ? Comparator.reverseOrder() : Comparator.naturalOrder();
    return new TestSorter() {
      @Override
      public @NotNull List<Class<?>> sorted(@NotNull List<Class<?>> testClasses, @NotNull ToIntFunction<? super Class<?>> ranker) {
        return ContainerUtil.sorted(testClasses,
                                    Comparator.<Class<?>>comparingInt(ranker).thenComparing(Class::getName, classNameComparator));
      }
    };
  }

  private void clearClasses() {
    myClassSet.clear();
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

  // called reflectively from `JUnit5TeamCityRunnerForTestsOnClasspath#createClassNameFilter`
  @SuppressWarnings("unused")
  public static boolean isClassIncluded(String className) {
    if (!INCLUDE_UNCONVENTIONALLY_NAMED_TESTS &&
        !className.endsWith("Test")) {
      return false;
    }

    if (ourFilter == null) {
      ourFilter = calcTestClassFilter("tests/testGroups.properties");
    }
    return (isIncludingPerformanceTestsRun() || isPerformanceTestsRun() == isPerformanceTest(null, className)) &&
           // no need to calculate bucket matching (especially that may break fair bucketing), if the test does not match the filter
           ourFilter.matches(className) &&
           matchesCurrentBucket(className);
  }

  public void fillTestCases(String rootPackage, List<? extends Path> classesRoots) {
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

    if (myClassSet.isEmpty()) { // nothing to test
      clearClasses();
    }

    t = (System.nanoTime() - t) / 1_000_000;
    System.out.println("Loaded " + getClassesCount() + " classes in " + t + " ms");

    if (!RUN_ONLY_AFFECTED_TESTS && getClassesCount() == 0 && !Boolean.getBoolean("idea.tests.ignoreJUnit3EmptySuite")) {
      // Specially formatted error message will fail the build
      // See https://www.jetbrains.com/help/teamcity/build-script-interaction-with-teamcity.html#BuildScriptInteractionwithTeamCity-ReportingMessagesForBuildLog
      System.out.println(
        "##teamcity[message text='Expected some junit 3 or junit 4 tests to be executed, but no test classes were found. " +
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
