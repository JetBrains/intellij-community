// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class JUnit5BazelRunner {
  private static final String bazelEnvSelfLocation = "SELF_LOCATION";
  private static final String bazelEnvTestTmpDir = "TEST_TMPDIR";
  private static final String bazelEnvRunFilesDir = "RUNFILES_DIR";
  private static final String bazelEnvJavaRunFilesDir = "JAVA_RUNFILES";
  private static final String bazelEnvTestSrcDir = "TEST_SRCDIR";
  private static final String bazelEnvTestBridgeTestOnly = "TESTBRIDGE_TEST_ONLY";

  private static final String jbEnvPrintSortedClasspath = "JB_TEST_PRINT_SORTED_CLASSPATH";
  private static final String jbEnvPrintTestSrcDirContent = "JB_TEST_PRINT_TEST_SRCDIR_CONTENT";
  private static final String jbEnvPrintEnv = "JB_TEST_PRINT_ENV";
  private static final String jbEnvPrintSystemProperties = "JB_TEST_PRINT_SYSTEM_PROPERTIES";
  // true by default. try as much as possible to run tests in sandbox
  private static final String jbEnvSandbox = "JB_TEST_SANDBOX";

  private static final ClassLoader ourClassLoader = Thread.currentThread().getContextClassLoader();
  private static final Launcher launcher = LauncherFactory.create();

  private static LauncherDiscoveryRequest getDiscoveryRequest() throws Throwable {
    return LauncherDiscoveryRequestBuilder.request()
      .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
      .selectors(getTestsSelectors(ourClassLoader))
      .filters(getTestFilters())
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

      // TODO Probably could be derived from the environment
      boolean sandbox = Boolean.parseBoolean(System.getenv(jbEnvSandbox));
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

      TestPlan testPlan = getTestPlan();
      if (!testPlan.containsTests()) {
        //see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR
        System.err.println("No tests found");
        System.exit(42);
      }

      var testExecutionListener = getTestExecutionListener();
      launcher.execute(testPlan, testExecutionListener);

      if (testExecutionListener instanceof ConsoleTestLogger && ((ConsoleTestLogger)testExecutionListener).hasTestsWithThrowableResults()) {
        System.err.println("Some tests failed");
        System.exit(1);
      }
    }
    catch (Throwable e) {
      // Internal error, exit with non-zero code

      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      System.exit(155);
    }
    finally {
      System.exit(0);
    }
  }

  private static TestExecutionListener getTestExecutionListener() {
    if (isUnderTeamCity()) {
      return new JUnit5TeamCityRunnerForTestAllSuite.TCExecutionListener();
    } else {
      return new ConsoleTestLogger();
    }
  }

  private static Filter<?>[] getTestFilters() {
    return new Filter[0];
  }

  private static List<? extends DiscoverySelector> getTestsSelectors(ClassLoader classLoader) throws Throwable {
    List<? extends DiscoverySelector> bazelTestClassSelector = getBazelTestClassSelectors(classLoader);
    if (!bazelTestClassSelector.isEmpty()) {
      return bazelTestClassSelector;
    }

    return getTestSelectorsByClassPathRoots(classLoader);
  }

  private static boolean isUnderTeamCity() {
    var teamCityVersion = System.getenv("TEAMCITY_VERSION");
    return teamCityVersion != null && !teamCityVersion.isEmpty();
  }

  // bazel-specific

  private static List<DiscoverySelector> getBazelTestClassSelectors(ClassLoader classLoader) {
    // value of --test_filter, if specified
    // https://bazel.build/reference/test-encyclopedia
    String testFilter = System.getenv(bazelEnvTestBridgeTestOnly);
    if (testFilter == null || testFilter.isBlank()) {
      return Collections.emptyList();
    }

    System.err.println("Test filter: " + testFilter);

    String[] parts = testFilter.split("#", 2);
    String classNamePart = parts[0];
    String className;
    if (!classNamePart.contains(".")) {
      className = findFullyQualifiedName(classNamePart, classLoader);
      if (className == null) {
        // TODO Add optional classpath info?
        throw new RuntimeException("Cannot find class by simple name: " + classNamePart);
      }
    }
    else {
      className = classNamePart;
    }

    if (parts.length == 2) {
      String methodName = parts[1];
      System.err.println("Selecting class: " + className);
      System.err.println("Selecting method: " + methodName);
      return List.of(selectMethod(classLoader, className, methodName));
    }
    else {
      System.err.println("Selecting class: " + className);
      return List.of(selectClass(classLoader, className));
    }
  }

  private static String findFullyQualifiedName(String simpleClassName, ClassLoader classLoader) {
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .ignoreClassVisibility()
      .addClassLoader(classLoader)
      .scan()
    ) {
      return scanResult.getAllClasses().stream()
        .filter(classInfo -> classInfo.getSimpleName().equals(simpleClassName))
        .map(classInfo -> classInfo.getName())
        .findFirst()
        .orElse(null);
    }
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
    // scan only relevant jars next to bazelEnvSelfLocation
    String bazelTestSelfLocation = System.getenv(bazelEnvSelfLocation);
    Path testJarsRoot = Path.of(bazelTestSelfLocation).getParent();

    try (Stream<Path> stream = Files.walk(testJarsRoot)) {
      return stream
        .filter(file -> !Files.isDirectory(file))
        .filter(p -> p.getFileName().toString().endsWith(".jar"))
        .collect(Collectors.toSet());
    }
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
    private final Set<TestIdentifier> testsWithThrowableResult = new HashSet<>();

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
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
      if (testIdentifier.isTest()) {
        result.getThrowable().ifPresent(testThrowable -> {
          if (!IgnoreException.isIgnoringThrowable(testThrowable)) {
            testsWithThrowableResult.add(testIdentifier);
          }
        });
      }

      if (testIdentifier.isTest()) {
        System.out.println("Finished: " + testIdentifier.getDisplayName() + " -> " + result.getStatus());
        result.getThrowable().ifPresent(t -> {
          t.printStackTrace(System.err);
          if (!IgnoreException.isIgnoringThrowable(t)) {
            testsWithThrowableResult.add(testIdentifier);
          }
        });
      }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
      System.out.println("Test plan finished with " + testPlan.countTestIdentifiers(TestIdentifier::isTest) + " tests.");
    }

    private Boolean hasTestsWithThrowableResults() {
      return !testsWithThrowableResult.isEmpty();
    }
  }
}
