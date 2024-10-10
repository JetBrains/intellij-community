// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.idea.ExcludeFromTestDiscovery;
import com.intellij.idea.IJIgnore;
import com.intellij.idea.IgnoreJUnit3;
import com.intellij.nastradamus.NastradamusClient;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.*;
import com.intellij.testFramework.bucketing.*;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

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
  public static final String IS_TESTS_DURATION_BUCKETING_ENABLED_FLAG = "idea.tests.duration.bucketing.enabled";
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

  /**
   * Distribute tests equally among the buckets
   */
  private static final boolean IS_FAIR_BUCKETING = Boolean.getBoolean(FAIR_BUCKETING_FLAG);

  /**
   * Distribute tests equally among buckets using tests duration data
   */
  public static final boolean IS_TESTS_DURATION_BUCKETING_ENABLED = Boolean.getBoolean(IS_TESTS_DURATION_BUCKETING_ENABLED_FLAG);

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

  public static final String COMMON_TEST_GROUPS_RESOURCE_NAME = "tests/testGroups.properties";

  private final HashSet<Class<?>> myClassSet = new HashSet<>();
  private final List<Throwable> myClassLoadingErrors = new ArrayList<>();
  private Class<?> myFirstTestClass;
  private Class<?> myLastTestClass;
  private final TestClassesFilter myTestClassesFilter;
  private final boolean myForceLoadPerformanceTests;
  private boolean myGetClassesCalled = false;

  private static final ThreadLocal<Boolean> ourBucketingSchemeInitRecursionLock = new ThreadLocal<>();
  private static final Lazy<BucketingScheme> ourBucketingScheme = LazyKt.lazy(() -> {
    BucketingScheme scheme;

    if (IS_TESTS_DURATION_BUCKETING_ENABLED) {
      scheme = new TestsDurationBucketingScheme();
    }
    else if (IS_NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED) {
      scheme = new NastradamusBucketingScheme();
    }
    else if (IS_FAIR_BUCKETING) {
      scheme = new CyclicCounterBucketingScheme();
    }
    else {
      scheme = new HashingBucketingScheme();
    }

    // run tests "as usual", but send the data to Nastradamus
    if (IS_NASTRADAMUS_SHADOW_DATA_COLLECTION_ENABLED && !(scheme instanceof NastradamusBucketingScheme)) {
      scheme = new NastradamusDataCollectingBucketingScheme(scheme);
    }

    if (ourBucketingSchemeInitRecursionLock.get() == Boolean.TRUE) throw new IllegalStateException("recursion detected");
    ourBucketingSchemeInitRecursionLock.set(Boolean.TRUE);
    try {
      scheme.initialize();
    } finally {
      ourBucketingSchemeInitRecursionLock.remove();
    }
    return scheme;
  });

  /**
   * @deprecated use `TestCaseLoader.Builder.defaults().withTestGroupsResourcePath(classFilterName).build();` instead
   */
  @Deprecated(forRemoval = true)
  public TestCaseLoader(String classFilterName) {
    this(classFilterName, false);
  }

  /**
   * @deprecated use `TestCaseLoader.Builder.defaults().withTestGroupsResourcePath(classFilterName).withForceLoadPerformanceTests(flag).build();` instead
   */
  @Deprecated(forRemoval = true)
  public TestCaseLoader(String classFilterName, boolean forceLoadPerformanceTests) {
    this(getFilter(getTestPatterns(), classFilterName, getTestGroups(), false), forceLoadPerformanceTests);
  }

  private TestCaseLoader(TestClassesFilter filter, boolean forceLoadPerformanceTests) {
    myForceLoadPerformanceTests = forceLoadPerformanceTests;
    myTestClassesFilter = filter;
    System.out.println("Using tests filter: " + myTestClassesFilter);
  }

  private static TestClassesFilter wrapAsCompositeTestClassesFilter(TestClassesFilter filter) {
    final TestClassesFilter affectedTestsFilter = affectedTestsFilter();
    if (affectedTestsFilter != null) {
      filter = new TestClassesFilter.And(filter, affectedTestsFilter);
    }
    final TestClassesFilter explicitTestsFilter = explicitTestsFilter();
    if (explicitTestsFilter != null) {
      filter = new TestClassesFilter.And(filter, explicitTestsFilter);
    }
    return filter;
  }

  private static TestClassesFilter calcTestClassFilter(@Nullable String patterns,
                                                       @Nullable List<@NotNull String> testGroupNames,
                                                       @Nullable String classFilterName) {
    if (!StringUtil.isEmpty(patterns)) {
      System.out.println("Using patterns: [" + patterns + "]");
      return new PatternListTestClassFilter(StringUtil.split(patterns, ";"));
    }
    if (testGroupNames == null) {
      testGroupNames = Collections.emptyList();
    }

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
    testGroupNameSet.remove(GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED);
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
      return Stream.of(System.getProperty("intellij.build.test.affected.classes.file", ""),
                       System.getProperty("idea.home.path", "") + "/discoveredTestClasses.txt")
        .filter(Predicate.not(StringUtil::isEmptyOrSpaces))
        .map(Path::of)
        .filter(Files::isRegularFile)
        .findFirst()
        .map(path -> {
          System.out.println("Loading affected tests patterns from " + path);
          return loadTestsFilterFromFile(path, true);
        }).orElse(null);
    }
    else {
      System.out.println("Affected tests discovery is disabled. Will run with the standard test filter");
    }
    return null;
  }

  private static @Nullable TestClassesFilter explicitTestsFilter() {
    String fileName = System.getProperty("intellij.build.test.list.file");
    if (fileName != null && !fileName.isBlank()) {
      Path path = Path.of(fileName);
      if (Files.isRegularFile(path)) {
        System.out.println("Loading explicit tests list from " + path);
        return loadTestsFilterFromFile(path, false);
      }
    }
    return null;
  }

  private static @Nullable TestClassesFilter loadTestsFilterFromFile(Path path, boolean linesArePatterns) {
    try {
      List<String> lines = Files.readAllLines(path);
      if (lines.isEmpty()) return null;
      if (ContainerUtil.and(lines, String::isBlank)) return null;
      return linesArePatterns ? new PatternListTestClassFilter(lines) : new NameListTestClassFilter(lines);
    }
    catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private static List<String> getTestGroups() {
    return StringUtil.split(System.getProperty("intellij.build.test.groups", System.getProperty("idea.test.group", "")).trim(), ";");
  }

  private boolean isPotentiallyTestCase(String className, String moduleName) {
    String classNameWithoutPackage = StringsKt.substringAfterLast(className, '.', className);
    if (!myForceLoadPerformanceTests && !shouldIncludePerformanceTestCase(classNameWithoutPackage)) return false;
    if (!myTestClassesFilter.matches(className, moduleName)) return false;
    if (myFirstTestClass != null && className.equals(myFirstTestClass.getName())) return false;
    if (myLastTestClass != null && className.equals(myLastTestClass.getName())) return false;
    return true;
  }

  private boolean isClassTestCase(Class<?> testCaseClass, String moduleName, boolean initializing) {
    if (shouldAddTestCase(testCaseClass, moduleName, true) &&
        testCaseClass != myFirstTestClass &&
        testCaseClass != myLastTestClass &&
        TestFrameworkUtil.canRunTest(testCaseClass)) {

      // fair bucketing initialization or warmup caches from nastradamus (if it's not fully initialized)
      if (initializing) return true;

      if (SelfSeedingTestCase.class.isAssignableFrom(testCaseClass) || matchesCurrentBucket(testCaseClass.getName())) {
        return true;
      }
    }

    return false;
  }

  /**
   * @return true iff this {@code testIdentifier} matches current testing settings: number of buckets and bucket index. {@code testIdentifier} may
   * be something identifying a test: test class or feature file name
   * @apiNote logic for bucketing tests into different bucket configurations.
   * @see TestCaseLoader#TEST_RUNNERS_COUNT
   * @see TestCaseLoader#TEST_RUNNER_INDEX
   */
  public static boolean matchesCurrentBucket(@NotNull String testIdentifier) {
    if (!shouldBucketTests()) return true;
    return ourBucketingScheme.getValue().matchesCurrentBucket(testIdentifier);
  }

  @ApiStatus.Internal
  public static List<Class<?>> loadClassesForWarmup() {
    var groupsTestCaseLoader = TestCaseLoader.Builder.fromDefaults().forWarmup().build();
    groupsTestCaseLoader.fillTestCases("", TestAll.getClassRoots(), true);
    var testCaseClasses = groupsTestCaseLoader.getClasses(false);

    System.out.printf("Finishing warmup initialization. Found %s classes%n", testCaseClasses.size());

    if (testCaseClasses.isEmpty()) {
      System.err.println("Fair bucketing or Nastradamus is enabled, but 0 test classes were found for warmup");
    }

    return testCaseClasses;
  }

  public static void sendTestRunResultsToNastradamus() {
    // Don't initialize if it wasn't used
    if (!ourBucketingScheme.isInitialized()) return;

    BucketingScheme scheme = ourBucketingScheme.getValue();
    if (!(scheme instanceof NastradamusBucketingScheme)) return;
    NastradamusClient client = ((NastradamusBucketingScheme)scheme).getNastradamusClient();
    if (client == null) return;

    try {
      var testRunRequest = client.collectTestRunResults();
      client.sendTestRunResults(testRunRequest, IS_NASTRADAMUS_TEST_DISTRIBUTOR_ENABLED);
    }
    catch (Exception e) {
      System.err.println("Unexpected error happened during sending test results to Nastradamus");
      e.printStackTrace();
    }
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
    if (Modifier.isAbstract(testCaseClass.getModifiers())) return false;

    if (checkForExclusion) {
      if (shouldExcludeTestClass(moduleName, testCaseClass)) return false;
    }

    if (TestCase.class.isAssignableFrom(testCaseClass) || TestSuite.class.isAssignableFrom(testCaseClass)) {
      return true;
    }

    try {
      final Method suiteMethod = testCaseClass.getMethod("suite");
      if (Test.class.isAssignableFrom(suiteMethod.getReturnType()) && Modifier.isStatic(suiteMethod.getModifiers())) {
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

    return !myTestClassesFilter.matches(className, moduleName) ||
           testCaseClass.isAnnotationPresent(IJIgnore.class) ||
           testCaseClass.isAnnotationPresent(IgnoreJUnit3.class) ||
           isExcludeFromTestDiscovery(testCaseClass);
  }

  private static boolean isExcludeFromTestDiscovery(Class<?> c) {
    return RUN_WITH_TEST_DISCOVERY && getAnnotationInHierarchy(c, ExcludeFromTestDiscovery.class) != null;
  }

  private void loadTestCases(final String moduleName, final Collection<String> classNamesIterator, boolean initialization) {
    for (String className : classNamesIterator) {
      if (!isPotentiallyTestCase(className, moduleName)) {
        continue;
      }
      try {
        Class<?> candidateClass = Class.forName(className, false, getClassLoader());
        if (isClassTestCase(candidateClass, moduleName, initialization)) {
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
    return getClasses(true);
  }

  List<Class<?>> getClasses(boolean includeFirstAndLast) {
    myGetClassesCalled = true;
    List<Class<?>> result = new ArrayList<>(myClassSet.size() + (includeFirstAndLast ? 2 : 0));

    if (includeFirstAndLast && myFirstTestClass != null) {
      result.add(myFirstTestClass);
    }

    result.addAll(loadTestSorter().sorted(myClassSet.stream().toList(), TestCaseLoader::getRank));

    if (includeFirstAndLast && myLastTestClass != null) {
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

    // use Nastradamus test sorter if no other is specified
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

  // We assume that getPatterns and getTestGroups won't change during execution
  @ApiStatus.Internal
  public record TestClassesFilterArgs(
    @Nullable String patterns, @Nullable List<@NotNull String> testGroupNames, @Nullable String testGroupsResourcePath
  ) {
  }

  @ApiStatus.Internal
  public static TestClassesFilterArgs getCommonTestClassesFilterArgs() {
    return ourCommonTestClassesFilterArgs.getValue();
  }

  private static final Lazy<TestClassesFilterArgs> ourCommonTestClassesFilterArgs =
    LazyKt.lazy(() -> {
      return new TestClassesFilterArgs(getTestPatterns(), getTestGroups(), COMMON_TEST_GROUPS_RESOURCE_NAME);
    });

  private static final Lazy<TestClassesFilter> ourCommonTestClassesFilter =
    LazyKt.lazy(() -> {
      TestClassesFilterArgs args = ourCommonTestClassesFilterArgs.getValue();
      TestClassesFilter filter = calcTestClassFilter(args.patterns, args.testGroupNames, args.testGroupsResourcePath);
      System.out.println("Initialized tests filter: " + filter);
      return filter;
    });

  // We assume that getPatterns and getTestGroups won't change during execution
  private static final Lazy<TestClassesFilter> ourCommonCompositeTestClassesFilter =
    LazyKt.lazy(() -> {
      TestClassesFilter filter = wrapAsCompositeTestClassesFilter(ourCommonTestClassesFilter.getValue());
      System.out.println("Initialized composite tests filter: " + filter);
      return filter;
    });


  // called reflectively from `JUnit5TeamCityRunnerForTestsOnClasspath#createClassNameFilter`
  @SuppressWarnings("unused")
  public static boolean isClassNameIncluded(String className) {
    if (!ClassFinder.isSuitableTestClassName(className, INCLUDE_UNCONVENTIONALLY_NAMED_TESTS)) {
      return false;
    }
    if ("_FirstInSuiteTest".equals(className) || "_LastInSuiteTest".equals(className)) {
      return false;
    }

    return (isIncludingPerformanceTestsRun() || isPerformanceTestsRun() == isPerformanceTest(null, className)) &&
           ourCommonCompositeTestClassesFilter.getValue().matches(className);
  }

  // called reflectively from `JUnit5TeamCityRunnerForTestsOnClasspath#createPostDiscoveryFilter`
  @SuppressWarnings("unused")
  public static boolean isClassIncluded(String className) {
    // JUnit 5 might rediscover `@Nested` tests if they were previously filtered out by `isClassNameIncluded`,
    // but their host class was not filtered out. Let's not remove them again based on `ourFilter.matches(className)`,
    // so not checking for `isClassNameIncluded` here.
    return matchesCurrentBucket(className);
  }

  public void fillTestCases(String rootPackage, List<? extends Path> classesRoots) {
    fillTestCases(rootPackage, classesRoots, false);
  }

  private void fillTestCases(String rootPackage, List<? extends Path> classesRoots, boolean warmUpPhase) {
    if (myGetClassesCalled) {
      throw new IllegalStateException("Cannot fill more classes after 'getClasses' was already called");
    }
    final String relevantJarsRoot = System.getProperty("intellij.test.jars.location");
    boolean noRelevantJarsRoot = StringUtil.isEmptyOrSpaces(relevantJarsRoot);
    long t = System.nanoTime();

    for (Path classesRoot : classesRoots) {
      String fileName = classesRoot.getFileName().toString();
      String moduleName = fileName;
      if (fileName.endsWith(".jar")) {
        if (noRelevantJarsRoot || !classesRoot.startsWith(relevantJarsRoot)) {
          continue;
        } else {
          // .../idea-compile-parts-v2/test/intellij.java.compiler.tests/$sha256.jar
          moduleName = classesRoot.getParent().getFileName().toString();
        }
      }
      int count = getClassesCount();
      ClassFinder classFinder = new ClassFinder(classesRoot, rootPackage, INCLUDE_UNCONVENTIONALLY_NAMED_TESTS);
      loadTestCases(moduleName, classFinder.getClasses(), warmUpPhase);
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

    if (!warmUpPhase && !RUN_ONLY_AFFECTED_TESTS && getClassesCount() == 0 && !Boolean.getBoolean("idea.tests.ignoreJUnit3EmptySuite")) {
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

  @ApiStatus.Experimental
  public static class Builder {
    private String myTestGroupsResourcePath;
    private String myPatterns;
    private List<String> myTestGroups;
    private boolean myForceLoadPerformanceTests = false;
    private boolean myWarmup = false;

    private Builder() {
    }

    public static Builder fromEmpty() {
      return new Builder();
    }

    public static Builder fromDefaults() {
      return new Builder().withDefaults();
    }

    private Builder withDefaults() {
      myPatterns = getTestPatterns();
      myTestGroups = getTestGroups();
      myTestGroupsResourcePath = COMMON_TEST_GROUPS_RESOURCE_NAME;
      return this;
    }

    public Builder withTestGroups(List<String> groups) {
      myTestGroups = groups;
      return this;
    }

    public Builder withTestGroupsResourcePath(String resourcePath) {
      myTestGroupsResourcePath = resourcePath;
      return this;
    }

    public Builder withPatterns(String patterns) {
      myPatterns = patterns;
      return this;
    }

    public Builder withForceLoadPerformanceTests(boolean flag) {
      myForceLoadPerformanceTests = flag;
      return this;
    }

    Builder forWarmup() {
      myWarmup = true;
      return this;
    }

    public TestCaseLoader build() {
      if (myPatterns == null && myTestGroups == null) {
        throw new IllegalStateException("Either withPatterns, withTestGroups, or fromDefault should be called");
      }
      TestClassesFilter filter = getFilter(myPatterns, myTestGroupsResourcePath, myTestGroups, myWarmup);
      return new TestCaseLoader(filter, myForceLoadPerformanceTests);
    }
  }

  private static TestClassesFilter getFilter(@Nullable String patterns,
                                             @Nullable String testGroupsResourcePath,
                                             @Nullable List<@NotNull String> testGroups,
                                             boolean warmup) {
    TestClassesFilter filter;
    if (ourCommonTestClassesFilterArgs.getValue().equals(new TestClassesFilterArgs(patterns, testGroups, testGroupsResourcePath))) {
      if (warmup) {
        filter = ourCommonTestClassesFilter.getValue();
      }
      else {
        filter = ourCommonCompositeTestClassesFilter.getValue();
      }
    }
    else {
      filter = calcTestClassFilter(patterns, testGroups, testGroupsResourcePath);
      if (!warmup) {
        filter = wrapAsCompositeTestClassesFilter(filter);
      }
    }
    return filter;
  }
}
