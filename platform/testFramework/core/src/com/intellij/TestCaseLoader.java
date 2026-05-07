// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.idea.ExcludeFromTestDiscovery;
import com.intellij.idea.IJIgnore;
import com.intellij.idea.IgnoreJUnit3;
import com.intellij.openapi.application.ArchivedCompilationContextUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.TeamCityLogger;
import com.intellij.testFramework.TestFrameworkUtil;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.bucketing.BucketingScheme;
import com.intellij.testFramework.bucketing.HashingBucketingScheme;
import com.intellij.testFramework.bucketing.TestsDurationBucketingScheme;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.Decompressor;
import com.intellij.util.lang.UrlClassLoader;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import kotlin.Lazy;
import kotlin.LazyKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace", "TestOnlyProblems"})
public class TestCaseLoader {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  public static final String PERFORMANCE_TESTS_ONLY_FLAG = "idea.performance.tests";
  public static final String INCLUDE_PERFORMANCE_TESTS_FLAG = "idea.include.performance.tests";
  public static final String INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG = "idea.include.unconventionally.named.tests";
  public static final String TEST_RUNNERS_COUNT_FLAG = "idea.test.runners.count";
  public static final String TEST_RUNNER_INDEX_FLAG = "idea.test.runner.index";
  public static final String VERBOSE_LOG_ENABLED_FLAG = "idea.test.log.verbose";
  public static final String IS_TESTS_DURATION_BUCKETING_ENABLED_FLAG = "idea.tests.duration.bucketing.enabled";

  private static final boolean PERFORMANCE_TESTS_ONLY = Boolean.getBoolean(PERFORMANCE_TESTS_ONLY_FLAG);
  private static final boolean INCLUDE_PERFORMANCE_TESTS = Boolean.getBoolean(INCLUDE_PERFORMANCE_TESTS_FLAG);
  private static final boolean INCLUDE_UNCONVENTIONALLY_NAMED_TESTS = Boolean.getBoolean(INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG);
  private static final boolean RUN_WITH_TEST_DISCOVERY = System.getProperty("test.discovery.listener") != null;
  public static final boolean IS_VERBOSE_LOG_ENABLED = Boolean.getBoolean(VERBOSE_LOG_ENABLED_FLAG);

  public static final int TEST_RUNNERS_COUNT = Integer.parseInt(System.getProperty(TEST_RUNNERS_COUNT_FLAG, "1"));
  public static final int TEST_RUNNER_INDEX = Integer.parseInt(System.getProperty(TEST_RUNNER_INDEX_FLAG, "0"));

  /**
   * Distribute tests equally among buckets using tests duration data
   */
  public static final boolean IS_TESTS_DURATION_BUCKETING_ENABLED = Boolean.getBoolean(IS_TESTS_DURATION_BUCKETING_ENABLED_FLAG);

  /**
   * An implicit group which includes all tests from all defined groups and tests which don't belong to any group.
   */
  private static final String ALL_TESTS_GROUP = "ALL";

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
    else {
      scheme = new HashingBucketingScheme();
    }

    if (ourBucketingSchemeInitRecursionLock.get() == Boolean.TRUE) throw new IllegalStateException("recursion detected");
    ourBucketingSchemeInitRecursionLock.set(Boolean.TRUE);
    try {
      TeamCityLogger.block("Initializing bucketing scheme ...", () -> {
        scheme.initialize();
      });
    } finally {
      ourBucketingSchemeInitRecursionLock.remove();
    }
    return scheme;
  });

  private TestCaseLoader(TestClassesFilter filter, boolean forceLoadPerformanceTests) {
    myForceLoadPerformanceTests = forceLoadPerformanceTests;
    myTestClassesFilter = filter;
    System.out.println("Using tests filter: " + myTestClassesFilter);
  }

  private static TestClassesFilter calcTestClassFilter(@Nullable List<@NotNull String> patterns,
                                                       @Nullable List<@NotNull String> testGroupNames,
                                                       @Nullable String classFilterName) {
    if (!ContainerUtil.isEmpty(patterns)) {
      System.out.println("Using patterns: " + patterns);
      return new PatternListTestClassFilter(patterns);
    }
    if (ContainerUtil.isEmpty(testGroupNames)) throw new IllegalArgumentException("No test groups specified");

    if (testGroupNames.contains(ALL_TESTS_GROUP)) {
      System.out.println("Using all classes");
      return TestClassesFilter.ALL_CLASSES;
    }

    String testGroupRoots = System.getProperty("test.group.roots");
    if (testGroupRoots == null) throw new RuntimeException("No test group roots specified");

    List<Path> groupingFiles = ContainerUtil.map(testGroupRoots.split(File.pathSeparator), Paths::get);
    TeamCityLogger.block("Loading test groups from ...", () -> {
      System.out.println("Loading test groups from: " + groupingFiles);
    });
    MultiMap<String, String> groups = MultiMap.createLinked();
    for (Path file : groupingFiles) {
      try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
        GroupBasedTestClassFilter.readGroups(reader, groups);
      }
      catch (IOException e) {
        throw new RuntimeException("Failed to load test groups from " + file, e);
      }
    }
    System.out.println("Using test groups: " + testGroupNames);
    Set<String> testGroupNameSet = new HashSet<>(testGroupNames);
    testGroupNameSet.removeAll(groups.keySet());
    testGroupNameSet.remove(GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED);
    if (!testGroupNameSet.isEmpty()) {
      throw new RuntimeException("Unknown test groups: " + testGroupNameSet);
    }
    return new GroupBasedTestClassFilter(groups, testGroupNames);
  }

  private static @Unmodifiable List<String> getTestPatterns() {
    return StringUtil.split(System.getProperty("intellij.build.test.patterns", ""), ";");
  }

  private static @Unmodifiable List<String> getTestGroups() {
    return StringUtil.split(System.getProperty("intellij.build.test.groups", ""), ";");
  }

  private boolean isPotentiallyTestCase(String className, String moduleName) {
    if (!myTestClassesFilter.matches(className, moduleName)) return false;
    if (myFirstTestClass != null && className.equals(myFirstTestClass.getName())) return false;
    if (myLastTestClass != null && className.equals(myLastTestClass.getName())) return false;
    // Ignore those classes even if they're not set as myFirstTestClass and myLastTestClass
    if (className.equals("_FirstInSuiteTest")) return false;
    if (className.equals("_LastInSuiteTest")) return false;
    return true;
  }

  private boolean isClassTestCase(Class<?> testCaseClass, String moduleName) {
    if (shouldAddTestCase(testCaseClass, moduleName, true) &&
        testCaseClass != myFirstTestClass &&
        testCaseClass != myLastTestClass &&
        TestFrameworkUtil.canRunTest(testCaseClass)) {
      return true;
    }

    return false;
  }

  /**
   * @return true iff this {@code testIdentifier} matches current testing settings: number of buckets and bucket index. {@code testIdentifier} may
   * be something identifying a test: test class or feature file name
   * @apiNote logic for bucketing tests into different bucket configurations.
   * @see TestCaseLoader#TEST_RUNNERS_COUNT
   * @see TestCaseLoader#TEST_RUNNER_INDEX
   *
   * called reflectively from `com.intellij.tests.JUnit5TeamCityRunner.BucketingClassNameFilter`
   */
  public static boolean matchesCurrentBucket(@NotNull String testIdentifier) {
    if (!shouldBucketTests()) return true;
    return ourBucketingScheme.getValue().matchesCurrentBucket(testIdentifier);
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
    if (!myForceLoadPerformanceTests && !shouldIncludePerformanceTestCase(testCaseClass)) return true;
    String className = testCaseClass.getName();

    return !myTestClassesFilter.matches(className, moduleName) ||
           testCaseClass.isAnnotationPresent(IJIgnore.class) ||
           testCaseClass.isAnnotationPresent(IgnoreJUnit3.class) ||
           isExcludeFromTestDiscovery(testCaseClass);
  }

  private static boolean isExcludeFromTestDiscovery(Class<?> c) {
    return RUN_WITH_TEST_DISCOVERY && getAnnotationInHierarchy(c, ExcludeFromTestDiscovery.class) != null;
  }

  private void loadTestCases(final String moduleName, final Collection<String> classNamesIterator) {
    for (String className : classNamesIterator) {
      if (!isPotentiallyTestCase(className, moduleName)) {
        continue;
      }
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
    return Thread.currentThread().getContextClassLoader();
  }

  public List<Throwable> getClassLoadingErrors() {
    return myClassLoadingErrors;
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

    result.addAll(ContainerUtil.sorted(myClassSet.stream().toList(), Comparator.comparing(Class::getName, Comparator.naturalOrder())));

    if (includeFirstAndLast && myLastTestClass != null) {
      result.add(myLastTestClass);
    }

    if (IS_VERBOSE_LOG_ENABLED) {
      System.out.println("Sorted classes: ");
      result.forEach(clazz -> System.out.println(clazz.getName()));
    }
    return result;
  }

  private void clearClasses() {
    myClassSet.clear();
    myFirstTestClass = null;
    myLastTestClass = null;
  }

  // called reflectively from `com.intellij.tests.JUnit5TeamCityRunner.PerformancePostDiscoveryFilter`
  public static boolean isPerformanceTestsRun() {
    return PERFORMANCE_TESTS_ONLY;
  }

  // called reflectively from `com.intellij.tests.JUnit5TeamCityRunner.PerformancePostDiscoveryFilter`
  public static boolean isIncludingPerformanceTestsRun() {
    return INCLUDE_PERFORMANCE_TESTS;
  }

  public static boolean shouldIncludePerformanceTestCase(Class<?> aClass) {
    return isIncludingPerformanceTestsRun() || isPerformanceTestsRun() || !isPerformanceTest(null, aClass);
  }

  static boolean isPerformanceTest(String methodName, Class<?> aClass) {
    return TestFrameworkUtil.isPerformanceTest(methodName, aClass);
  }

  // We assume that getPatterns and getTestGroups won't change during execution
  @ApiStatus.Internal
  public record TestClassesFilterArgs(
    @Nullable List<@NotNull String> patterns, @Nullable List<@NotNull String> testGroupNames, @Nullable String testGroupsResourcePath
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

  // called reflectively from `com.intellij.tests.JUnit5TeamCityRunner.CommonTestClassesFilter`
  @SuppressWarnings("unused")
  public static boolean isClassNameIncluded(String className) {
    if (!ClassFinder.isSuitableTestClassName(className, INCLUDE_UNCONVENTIONALLY_NAMED_TESTS)) {
      return false;
    }
    if ("_FirstInSuiteTest".equals(className) || "_LastInSuiteTest".equals(className)) {
      return false;
    }

    return ourCommonTestClassesFilter.getValue().matches(className);
  }

  // called reflectively from `com.intellij.tests.bazel.bucketing.BucketsPostDiscoveryFilter`
  @SuppressWarnings("unused")
  public static boolean isClassIncluded(Class<?> aClass) {
    // JUnit 5 might rediscover `@Nested` tests if they were previously filtered out by `isClassNameIncluded`,
    // but their host class was not filtered out. Let's not remove them again based on `ourFilter.matches(className)`,
    // so not checking for `isClassNameIncluded` here.
    return matchesCurrentBucket(aClass.getName());
  }

  public void fillTestCases(String rootPackage, List<? extends Path> classesRoots) {
    if (myGetClassesCalled) {
      throw new IllegalStateException("Cannot fill more classes after 'getClasses' was already called");
    }
    String relevantJarsRoot = ArchivedCompilationContextUtil.getArchivedCompiledClassesLocation();
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
      loadTestCases(moduleName, classFinder.getClasses());
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

    if (getClassesCount() == 0) {
      // Specially formatted error message will fail the build
      // See https://www.jetbrains.com/help/teamcity/build-script-interaction-with-teamcity.html#BuildScriptInteractionwithTeamCity-ReportingMessagesForBuildLog
      System.out.println("##teamcity[message text='Expected some junit 3 or junit 4 tests to be executed, but no test classes were found' status='ERROR']");
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

  public static @Unmodifiable List<Path> getClassRoots() {
    return TeamCityLogger.block("Collecting tests from ...", () -> {
      List<Path> paths = doGetClassRoots();
      saveTestRootsForDebug(paths);
      return paths;
    });
  }

  private static @Unmodifiable List<Path> doGetClassRoots() {
    String jarsToRunTestsFrom = System.getProperty("jar.dependencies.to.tests");
    if (jarsToRunTestsFrom != null) {
      String[] jars = jarsToRunTestsFrom.split(";");
      List<Path> classpath = ContainerUtil.map(Objects.requireNonNull(System.getProperty("java.class.path")).split(File.pathSeparator), Paths::get);
      List<Path> testPaths = Arrays.stream(jars)
        .map(jarName -> {
               List<? extends Path> resultJars = ContainerUtil.filter(classpath, path -> path.getFileName().toString().startsWith(jarName));
               if (resultJars.size() != 1) {
                 String classpathPretty = classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
                 throw new IllegalStateException(
                   (resultJars.isEmpty() ? "Cannot find " : "More than one ") + jarName + " in " + classpathPretty
                 );
               }

               return resultJars.get(0);
             }
        )
        .map(Path::normalize)
        .map(jar -> {
          try {
            if (!Files.exists(jar)) {
              throw new IllegalStateException(jar + " doesn't exist");
            }

            String jarNameWithoutExtension = StringUtil.substringBefore(jar.getFileName().toString(), ".");
            Path out = Paths.get(PathManager.getHomePath(), "out", "jar-dependencies-to-test", jarNameWithoutExtension);
            new Decompressor.Zip(jar).extract(out);
            return out;
          }
          catch (IOException e) {
            throw new IllegalStateException(e);
          }
        })
        .collect(Collectors.toList());

      System.out.println("Collecting tests from roots specified by jar.dependencies.to.tests system property");
      return testPaths;
    }

    String testRoots = System.getProperty("test.roots");
    if (testRoots != null) {
      System.out.println("Collecting tests from roots specified by test.roots system property");
      return ContainerUtil.map(testRoots.split(File.pathSeparator), Paths::get);
    }
    ClassLoader loader = TestCaseLoader.class.getClassLoader();
    if (loader instanceof URLClassLoader) {
      System.out.println("Collecting tests from TestCaseLoader class loader (" + URLClassLoader.class.getName() + ")");
      return ContainerUtil.map(getClassRoots(((URLClassLoader)loader).getURLs()), url -> Paths.get(url.toUri()));
    }
    if (loader instanceof UrlClassLoader) {
      List<Path> paths = ((UrlClassLoader)loader).getFiles();
      System.out.println("Collecting tests from TestCaseLoader class loader (" + UrlClassLoader.class.getName() + ")");
      return paths;
    }
    System.out.println("Collecting tests from java.class.path system property");
    return ContainerUtil.map(System.getProperty("java.class.path").split(File.pathSeparator), Paths::get);
  }

  private static List<Path> getClassRoots(URL[] urls) {
    List<Path> classLoaderRoots = ContainerUtil.map(urls, url -> {
      try {
        return Paths.get(url.toURI());
      }
      catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    });
    System.out.println("Collecting tests from " + classLoaderRoots);
    return classLoaderRoots;
  }

  private static void saveTestRootsForDebug(@NotNull List<Path> paths) {
    try {
      Path tempFile = Files.createTempFile("TestCaseLoader-test-roots-path", ".txt");
      System.out.println("Saving test roots for further debugging to " + tempFile);
      Files.writeString(tempFile, String.join("\n", ContainerUtil.map(paths, Path::toString)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @ApiStatus.Experimental
  public static class Builder {
    private String myTestGroupsResourcePath;
    private List<String> myPatterns;
    private List<String> myTestGroups;
    private boolean myForceLoadPerformanceTests = false;

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

    public Builder withPatterns(List<String> patterns) {
      myPatterns = patterns;
      return this;
    }

    public Builder withForceLoadPerformanceTests(boolean flag) {
      myForceLoadPerformanceTests = flag;
      return this;
    }

    public TestCaseLoader build() {
      if (myPatterns == null && myTestGroups == null) {
        throw new IllegalStateException("Either withPatterns, withTestGroups, or fromDefault should be called");
      }
      TestClassesFilter filter = getFilter(myPatterns, myTestGroupsResourcePath, myTestGroups);
      return new TestCaseLoader(filter, myForceLoadPerformanceTests);
    }
  }

  private static TestClassesFilter getFilter(@Nullable List<@NotNull String> patterns,
                                             @Nullable String testGroupsResourcePath,
                                             @Nullable List<@NotNull String> testGroups) {
    TestClassesFilter filter;
    if (ourCommonTestClassesFilterArgs.getValue().equals(new TestClassesFilterArgs(patterns, testGroups, testGroupsResourcePath))) {
      filter = ourCommonTestClassesFilter.getValue();
    }
    else {
      filter = calcTestClassFilter(patterns, testGroups, testGroupsResourcePath);
    }
    return filter;
  }
}
