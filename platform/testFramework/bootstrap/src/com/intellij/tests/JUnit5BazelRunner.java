// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import com.intellij.tests.bazel.BazelJUnitOutputListener;
import com.intellij.tests.bazel.IjSmTestExecutionListener;
import com.intellij.tests.bazel.bucketing.BucketsPostDiscoveryFilter;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class JUnit5BazelRunner {
  // compatible with https://github.com/bazelbuild/bazel/blob/master/src/java_tools/junitrunner/java/com/google/testing/junit/runner/BazelTestRunner.java
  private static final int EXIT_CODE_SUCCESS = 0;
  private static final int EXIT_CODE_TEST_FAILURE_OTHER = 1;
  private static final int EXIT_CODE_TEST_RUNNER_FAILURE = 2;
  private static final int EXIT_CODE_TEST_FAILURE_OOM = 137;

  private static final String bazelEnvSelfLocation = "SELF_LOCATION";
  private static final String bazelEnvTestTmpDir = "TEST_TMPDIR";
  private static final String bazelEnvRunFilesDir = "RUNFILES_DIR";
  private static final String bazelEnvJavaRunFilesDir = "JAVA_RUNFILES";
  private static final String bazelEnvTestSrcDir = "TEST_SRCDIR";
  private static final String bazelEnvTestBridgeTestOnly = "TESTBRIDGE_TEST_ONLY";
  private static final String bazelEnvXmlOutputFile = "XML_OUTPUT_FILE";

  private static final String jbEnvPrintSortedClasspath = "JB_TEST_PRINT_SORTED_CLASSPATH";
  private static final String jbEnvPrintTestSrcDirContent = "JB_TEST_PRINT_TEST_SRCDIR_CONTENT";
  private static final String jbEnvPrintEnv = "JB_TEST_PRINT_ENV";
  private static final String jbEnvPrintSystemProperties = "JB_TEST_PRINT_SYSTEM_PROPERTIES";
  private static final String jbEnvSandbox = "JB_TEST_SANDBOX";
  private static final String jbEnvXmlOutputFile = "JB_XML_OUTPUT_FILE";
  // Enable IntelliJ Service Messages stream from test process
  private static final String jbEnvIdeSmRun = "JB_IDE_SM_RUN";
  // Allows specifying an unambiguous test filter format that supports, e.g., method names with spaces in them
  private static final String jbEnvTestFilter = "JB_TEST_FILTER";
  // Allow rerun-failed selection via JUnit5 UniqueId list
  private static final String jbEnvTestUniqueIds = "JB_TEST_UNIQUE_IDS";

  private static final ClassLoader ourClassLoader = Thread.currentThread().getContextClassLoader();
  private static final Launcher launcher = LauncherFactory.create();

  private static final BucketsPostDiscoveryFilter bucketingPostDiscoveryFilter = new BucketsPostDiscoveryFilter();

  private static LauncherDiscoveryRequest getDiscoveryRequest() throws Throwable {
    List<? extends DiscoverySelector> bazelTestSelectors = getTestsSelectors(ourClassLoader);
    return LauncherDiscoveryRequestBuilder.request()
      .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
      .selectors(bazelTestSelectors)
      .filters(getTestFilters(bazelTestSelectors))
      .build();
  }

  private static List<? extends DiscoverySelector> getTestSelectorsByClassPathRoots(ClassLoader classLoader) throws Throwable {
    Set<Path> classPathRoots = getClassPathRoots(classLoader);
    return getSelectors(classPathRoots);
  }

  private static TestPlan getTestPlan() throws Throwable {
    LauncherDiscoveryRequest discoveryRequest = getDiscoveryRequest();
    return launcher.discover(discoveryRequest);
  }

  public static void main(String[] args) throws IOException {
    try {
      System.err.println("Running tests via " + JUnit5BazelRunner.class.getName());

      var isBazelTestRun = isBazelTestRun();
      if (!isBazelTestRun) {
        throw new RuntimeException("Missing expected env variable in bazel test environment.");
      }

      String bazelTestTestSrcDir = System.getenv(bazelEnvTestSrcDir);

      // set intellij.test.jars.location as a temporary workaround for debugger-agent.jar downloading
      System.setProperty("intellij.test.jars.location", bazelTestTestSrcDir);

      if (Boolean.parseBoolean(System.getenv(jbEnvPrintSortedClasspath))) {
        Arrays.stream(System.getProperty("java.class.path")
          .split(Pattern.quote(File.pathSeparator)))
          .sorted()
          .toList()
          .forEach(x -> System.err.println("CLASSPATH " + x));
      }

      if (Boolean.parseBoolean(System.getenv(jbEnvPrintEnv))) {
        System.getenv().entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(entry -> System.err.println("ENV " + entry.getKey() + "=" + entry.getValue()));
      }

      if (Boolean.parseBoolean(System.getenv(jbEnvPrintSystemProperties))) {
        System.getProperties().entrySet().stream()
          .sorted(Comparator.comparing(o -> o.getKey().toString()))
          .forEach(entry -> System.err.println("PROPERTY " + entry.getKey() + "=" + entry.getValue()));
      }

      String testSrcDir = System.getenv(bazelEnvTestSrcDir);
      if (testSrcDir == null) {
        throw new RuntimeException("Missing TEST_SRCDIR env variable in bazel test environment");
      }

      Path testSrcDirPath = Path.of(testSrcDir);
      if (!Files.isDirectory(testSrcDirPath)) {
        throw new RuntimeException("$TEST_SRCDIR is not a directory: " + testSrcDirPath);
      }

      if (Boolean.parseBoolean(System.getenv(jbEnvPrintTestSrcDirContent))) {
        try (var stream = Files.walk(testSrcDirPath)) {
          stream.forEach(path -> System.err.println("TEST_SRCDIR " + testSrcDirPath.relativize(path)));
        }
      }

      Path ideaHome;
      Path tempDir = getBazelTempDir();

      String jbEnvSandboxValue = System.getenv(jbEnvSandbox);
      if (jbEnvSandboxValue == null) {
        throw new RuntimeException("Missing " + jbEnvSandbox + " env variable in bazel test environment");
      }

      boolean sandbox = Boolean.parseBoolean(jbEnvSandboxValue);
      System.err.println("Use sandbox: " + sandbox);

      if (sandbox) {
        // Fully isolated tests
        ideaHome = tempDir.resolve("home");

        // Make com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources return true
        Files.createDirectories(ideaHome.resolve(".idea"));

        // org/jetbrains/intellij/build/dependencies/BuildDependenciesCommunityRoot.kt -> ctor
        Files.writeString(ideaHome.resolve("intellij.idea.community.main.iml"), "");
      }
      else {
        // Traditional arts: idea.home is set to monorepo checkout root
        ideaHome = guessBazelWorkspaceDir();
      }

      System.err.println("Using ideaHome: " + ideaHome);
      System.err.println("Using tempDir: " + tempDir);

      setBazelSandboxPaths(ideaHome, tempDir);

      System.out.println("Number of test engines: " + ServiceLoader.load(TestEngine.class).stream().count());

      var xmlOutputFile = getXmlOutputFile();

      // If bucketing filters out all tests, we emit a minimal JUnit XML with a single testsuite named "Bucketing".
      // See: com.intellij.tests.JUnit5BazelRunner.xmlReportBucketingTestsFilteringOut
      TestPlan testPlan = getTestPlan();
      if (!testPlan.containsTests()) {
        if (!bucketingPostDiscoveryFilter.hasExcludedClasses() && !bucketingPostDiscoveryFilter.hasIncludedClasses()) {
          //see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR
          System.err.println("No tests found");
          System.exit(42);
        } else {
          System.err.println("No tests executed: all tests were filtered out by bucketing.");
          // Emit an empty XML with a single testsuite explaining that bucketing filtered out all tests
          //This is emitted only in the scenario where:
          //- The JUnit TestPlan is empty (no tests to run), and
          //- The bucketing post-discovery filter did include/exclude classes
          //  (meaning discovery was successful, but all relevant tests belong to other buckets).
          xmlReportBucketingTestsFilteringOut(xmlOutputFile);
          System.exit(EXIT_CODE_SUCCESS);
        }
      }

      var testExecutionListeners = getTestExecutionListeners();
      // Add shutdown hook for SM listener to handle interrupts
      IjSmTestExecutionListener smListener = null;
      for (var l : testExecutionListeners) {
        if (l instanceof IjSmTestExecutionListener) { smListener = (IjSmTestExecutionListener) l; break; }
      }
      if (smListener != null) {
        IjSmTestExecutionListener finalSmListener = smListener;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> finalSmListener.closeForInterrupt(), "IjSmTestExecutionListenerShutdownHook"));
      }

      try (var bazelJUnitOutputListener = new BazelJUnitOutputListener(xmlOutputFile)) {
        Runtime.getRuntime()
          .addShutdownHook(
            new Thread(() -> bazelJUnitOutputListener.closeForInterrupt(), "BazelJUnitOutputListenerShutdownHook")
          );
        testExecutionListeners.add(bazelJUnitOutputListener);
        launcher.registerTestExecutionListeners(testExecutionListeners.toArray(TestExecutionListener[]::new));
        launcher.execute(testPlan);
      }

      if (testExecutionListeners.stream()
        .anyMatch(l -> l instanceof BazelJUnitOutputListener && ((BazelJUnitOutputListener)l).hasTestsWithThrowableResults())) {
        System.err.println("Some tests failed");
        System.exit(EXIT_CODE_TEST_FAILURE_OTHER);
      }
    }
    catch (OutOfMemoryError e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      System.exit(EXIT_CODE_TEST_FAILURE_OOM);
    }
    catch (Throwable e) {
      // Internal error, exit with non-zero code
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      System.exit(EXIT_CODE_TEST_RUNNER_FAILURE);
    }
    finally {
      // System.exit to exist even if some other non-background threads exist
      System.exit(EXIT_CODE_SUCCESS);
    }
  }

  private static Path getXmlOutputFile() throws IOException {
    String xmlOutputFile;
    // XML_OUTPUT_FILE is set by bazel itself and can't be overridden by `--test_env=XML_OUTPUT_FILE=<some_path>`
    String jbXmlOutputFile = System.getenv(jbEnvXmlOutputFile);
    if (jbXmlOutputFile != null && !jbXmlOutputFile.isBlank()) {
      xmlOutputFile = jbXmlOutputFile;
    } else {
      xmlOutputFile = System.getenv(bazelEnvXmlOutputFile);
    }
    Path xmlOut = xmlOutputFile != null ? Paths.get(xmlOutputFile) : Files.createTempFile("test", ".xml");
    Files.createDirectories(xmlOut.getParent());

    return xmlOut;
  }

  private static List<TestExecutionListener> getTestExecutionListeners() {
    List<TestExecutionListener> myListeners = new ArrayList<>();
    if (!isUnderTeamCity()) {
      if ("true".equals(System.getenv(jbEnvIdeSmRun))) {
        myListeners.add(new IjSmTestExecutionListener());
      } else {
        myListeners.add(new ConsoleTestLogger());
      }
    }
    return myListeners;
  }

  private static void addSelectorsFromJbEnv(ClassLoader classLoader, List<DiscoverySelector> out) {
    // We can use colons and semicolons as separators because they aren't allowed in identifiers neither in Java nor Kotlin
    // See https://kotlinlang.org/docs/reference/grammar.html

    // JB_TEST_UNIQUE_IDS: semicolon-separated list of JUnit5 UniqueIds
    String uniqueIds = System.getenv(jbEnvTestUniqueIds);
    if (uniqueIds != null && !uniqueIds.isBlank()) {
      for (String uid : uniqueIds.split(";")) {
        if (!uid.isEmpty()) {
          out.add(DiscoverySelectors.selectUniqueId(uid));
        }
      }
    }
    // JB_TEST_FILTER: semicolon-separated list of class, class:method, or class:method:comma_separated_parameter_type_names
    String methods = System.getenv(jbEnvTestFilter);
    if (methods != null && !methods.isBlank()) {
      for (String token : methods.split(";")) {
        if (token.isEmpty()) continue;
        String[] parts = token.split(":");
        switch (parts.length) {
          case 1:
            out.add(selectClass(classLoader, /*className*/ parts[0]));
            break;
          case 2:
            out.add(selectMethod(classLoader, /*className*/ parts[0], /*methodName*/ parts[1]));
            break;
          case 3:
            out.add(selectMethod(classLoader, /*className*/ parts[0], /*methodName*/ parts[1], /*parameterTypeNames*/ parts[2]));
            break;
        }
      }
    }
  }

  private static Filter<?>[] getTestFilters(List<? extends DiscoverySelector> bazelTestSelectors) {
    List<Filter<?>> filters = new ArrayList<>();
    filters.add(bucketingPostDiscoveryFilter);

    // value of --test_filter, if specified
    // https://bazel.build/reference/test-encyclopedia
    String testFilter = System.getenv(bazelEnvTestBridgeTestOnly);
    if (testFilter == null || testFilter.isBlank()) {
      return filters.toArray(new Filter[0]);
    }

    // If we already have precise selectors (method or uniqueId), don't also apply class name filter
    if (bazelTestSelectors.stream().allMatch(selector -> selector instanceof MethodSelector || selector instanceof UniqueIdSelector)) {
      return filters.toArray(new Filter[0]);
    }

    String[] parts = testFilter.split("#", 2);
    if (parts.length == 2) {
      throw new IllegalStateException("Method filters are not expected in name-based test filter");
    }
    String classNamePart = parts[0];
    ClassNameFilter classNameFilter = getClassNameFilter(classNamePart);

    filters.add(classNameFilter);
    return filters.toArray(new Filter[0]);
  }

  private static List<? extends DiscoverySelector> getTestsSelectors(ClassLoader classLoader) throws Throwable {
    // First, allow IDE-driven rerun-failed via explicit env vars
    List<DiscoverySelector> jbSelectors = new ArrayList<>();
    addSelectorsFromJbEnv(classLoader, jbSelectors);
    if (!jbSelectors.isEmpty()) {
      return jbSelectors;
    }

    // Next, Bazel's TESTBRIDGE_TEST_ONLY method selector (single class#method)
    List<? extends DiscoverySelector> bazelTestClassSelector = getBazelTestMethodSelectors(classLoader);
    if (!bazelTestClassSelector.isEmpty()) {
      return bazelTestClassSelector;
    }

    // Otherwise, discover from classpath roots
    return getTestSelectorsByClassPathRoots(classLoader);
  }

  private static boolean isUnderTeamCity() {
    var teamCityVersion = System.getenv("TEAMCITY_VERSION");
    return teamCityVersion != null && !teamCityVersion.isEmpty();
  }

  // bazel-specific

  private static List<MethodSelector> getBazelTestMethodSelectors(ClassLoader classLoader) {
    // value of --test_filter, if specified
    // https://bazel.build/reference/test-encyclopedia
    String testFilter = System.getenv(bazelEnvTestBridgeTestOnly);
    if (testFilter == null || testFilter.isBlank()) {
      return Collections.emptyList();
    }

    System.err.println("Test filter: " + testFilter);

    String[] parts = testFilter.split("#", 2);

    // build only method selectors, as filtering by class name only has to be done separately
    if (parts.length == 2) {
      String className = parts[0];
      String methodName = parts[1];
      System.err.println("Selecting class: " + className);
      System.err.println("Selecting method: " + methodName);
      return List.of(selectMethod(classLoader, className, methodName));
    } else {
      return Collections.emptyList();
    }
  }

  private static ClassNameFilter getClassNameFilter(String filterClassName) {
    String filterClassNameSimpleName;
    String filterClassNameFQN;
    int lastFilterClassNamePartDotIndex = filterClassName.lastIndexOf('.');
    if (lastFilterClassNamePartDotIndex < 0) {
      filterClassNameSimpleName = filterClassName;
      filterClassNameFQN = null;
    } else {
      filterClassNameSimpleName = filterClassName.substring(lastFilterClassNamePartDotIndex + 1);
      filterClassNameFQN = filterClassName;
    }

    if (!Character.isUpperCase(filterClassNameSimpleName.charAt(0))) {
      throw new IllegalArgumentException("Class name should start with uppercase letter: " + filterClassNameSimpleName);
    }

    return new ClassNameFilter() {
      @Override
      public FilterResult apply(String className) {
        if (filterClassNameFQN == null) {
          int lastClassNamePartDotIndex = className.lastIndexOf('.');
          String classNameSimpleName = className.substring(lastClassNamePartDotIndex + 1);
          if (classNameSimpleName.startsWith(filterClassNameSimpleName)) {
            return FilterResult.included(null);
          }
          else {
            return FilterResult.excluded(null);
          }
        }
        else {
          if (className.startsWith(filterClassNameFQN)) {
            return FilterResult.included(null);
          }
          else {
            return FilterResult.excluded(null);
          }
        }
      }
    };
  }

  private static Path getBazelTempDir() throws IOException {
    String tempDir = System.getenv(bazelEnvTestTmpDir);
    if (tempDir == null || tempDir.isBlank()) {
      throw new RuntimeException("Missing TEST_TMPDIR env variable in bazel test environment");
    }

    Path path = Path.of(tempDir);
    Files.createDirectories(path);
    return path.toAbsolutePath();
  }

  private static Boolean isBazelTestRun() {
    return Stream.of(bazelEnvSelfLocation, bazelEnvTestTmpDir, bazelEnvRunFilesDir, bazelEnvJavaRunFilesDir)
      .allMatch(bazelTestEnv -> {
        var bazelTestEnvValue = System.getenv(bazelTestEnv);
        return bazelTestEnvValue != null && !bazelTestEnvValue.isBlank();
      });
  }

  private static void setBazelSandboxPaths(Path ideaHomePath, Path tempDir) throws IOException {
    setSandboxPath("idea.home.path", ideaHomePath);

    setSandboxPath("idea.config.path", tempDir.resolve("config"));
    setSandboxPath("idea.system.path", tempDir.resolve("system"));

    String testUndeclaredOutputsDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    if (testUndeclaredOutputsDir != null) {
      setSandboxPath("idea.log.path", Path.of(testUndeclaredOutputsDir).resolve("logs"));
    }
    else {
      setSandboxPath("idea.log.path", tempDir.resolve("logs"));
    }

    setSandboxPath("java.util.prefs.userRoot", tempDir.resolve("userRoot"));
    setSandboxPath("java.util.prefs.systemRoot", tempDir.resolve("systemRoot"));
  }

  private static Path guessBazelWorkspaceDir() throws IOException {
    // see https://bazel.build/concepts/dependencies#data-dependencies
    String testSrcDir = System.getenv(bazelEnvTestSrcDir);
    if (testSrcDir == null) {
      throw new RuntimeException("Missing TEST_SRCDIR env variable in bazel test environment");
    }

    Path workDirPath = Path.of(testSrcDir);
    String communityMarkerFileName = "intellij.idea.community.main.iml";
    Path ultimateMarkerFile = workDirPath.resolve("community+").resolve(communityMarkerFileName);
    Path communityMarkerFile = workDirPath.resolve("_main").resolve(communityMarkerFileName);
    if (Files.exists(ultimateMarkerFile)) {
      // we are in the ultimate context run
      Path realUltimatePath = ultimateMarkerFile.toRealPath().getParent().getParent();
      if (!Files.exists(realUltimatePath.resolve(".ultimate.root.marker"))) {
        throw new RuntimeException("Missing .ultimate.root.marker file in " + realUltimatePath + " directory candidate");
      }
      return realUltimatePath;
    } else if (Files.exists(communityMarkerFile)) {
      //we are in the community context run
      return communityMarkerFile.toRealPath().getParent();
    } else {
      throw new RuntimeException("Cannot find marker files " + ultimateMarkerFile + " either " + communityMarkerFile);
    }
  }

  private static void setSandboxPath(String property, Path path) throws IOException {
    Files.createDirectories(path);
    setIfEmpty(property, path.toAbsolutePath().toString());
  }

  private static void setIfEmpty(String property, String value) {
    if (value == null) {
      return;
    }
    if (System.getProperty(property) == null) {
      System.setProperty(property, value);
    }
  }

  public static Set<Path> getClassPathRoots(ClassLoader classLoader) throws Throwable {
    // to get relevant jars for the current test target, we do the following:
    // - get the list of all the paths in classpath by getBaseUrls() using reflection
    // - get from this list only those paths, that located next to env.SELF_LOCATION
    // where SELF_LOCATION is the path to the test executable/script and set by Bazel automatically
    Method getBaseUrls = classLoader.getClass().getMethod("getBaseUrls");
    //noinspection unchecked
    List<Path> paths = (List<Path>)getBaseUrls.invoke(classLoader);

    String bazelTestSelfLocation = System.getenv(bazelEnvSelfLocation);
    Path bazelTestSelfLocationDir = Path.of(bazelTestSelfLocation).getParent().toAbsolutePath();
    return paths.stream()
      .filter(p -> bazelTestSelfLocationDir.equals(p.toAbsolutePath().getParent()))
      .collect(Collectors.toSet());
  }

  public static List<? extends DiscoverySelector> getSelectors(Set<Path> classPathRoots) {
    List<? extends DiscoverySelector> selectors;
    if (classPathRoots != null) {
      selectors = DiscoverySelectors.selectClasspathRoots(classPathRoots);
    }
    else {
      selectors = Collections.singletonList(DiscoverySelectors.selectPackage(""));
    }

    return selectors;
  }

  private static class ConsoleTestLogger implements TestExecutionListener {
    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
      System.out.println("Test plan started: " + testPlan.countTestIdentifiers(TestIdentifier::isTest) + " tests found.");
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
      if (testIdentifier.isTest()) {
        System.out.println("Started: " + testIdentifier.getDisplayName());
      }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
      if (testIdentifier.isTest()) {
        System.out.println("Skipped: " + testIdentifier.getDisplayName() + " (reason: " + reason + ")");
      }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
      System.out.println("Test plan finished with " + testPlan.countTestIdentifiers(TestIdentifier::isTest) + " tests.");
    }
  }


  /**
   * If all discovered test classes are filtered out by the bucketing mechanism
   * (i.e., the current runner/bucket owns none of the selected tests),
   * `JUnit5BazelRunner` writes a minimal JUnit XML to `XML_OUTPUT_FILE`
   * with a single testsuite named `Bucketing` and zero tests.
   *
   * Do not interpret this as "no tests found" across the entire target.
   * Other buckets likely execute the actual tests.
   *
   * The intent is a compact, unambiguous signal that this runner instance had nothing to execute
   * due to bucketing. Detailed per-test entries are unnecessary and could bloat outputs.
   * The XML is a valid JUnit format. Most parsers accept suites with zero tests. The message is in `system-out`.
   */
  private static void xmlReportBucketingTestsFilteringOut(Path xmlOutputFile) {
    try {
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                   "<testsuites>" +
                   "<testsuite name=\"Bucketing\" tests=\"0\" failures=\"0\" errors=\"0\" skipped=\"0\">" +
                   "<system-out>No tests executed: all tests were filtered out by bucketing.</system-out>" +
                   "</testsuite>" +
                   "</testsuites>";
      Files.writeString(xmlOutputFile, xml);
    }
    catch (IOException t) {
      // Non-fatal: we still exit successfully, but log the problem to stderr
      System.err.println("Failed to write XML_OUTPUT_FILE for bucketing-empty plan: " + t.getMessage());
    }
  }
}
