// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.launcher.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class JUnit5BazelRunner extends JUnit5BaseRunner {
  private static final String bazelEnvSelfLocation = "SELF_LOCATION";
  private static final String bazelEnvTestTmpDir = "TEST_TMPDIR";
  private static final String bazelEnvRunFilesDir = "RUNFILES_DIR";
  private static final String bazelEnvJavaRunFilesDir = "JAVA_RUNFILES";
  private static final String bazelEnvTestSrdDir = "TEST_SRCDIR";
  private static final String bazelEnvTestBridgeTestOnly = "TESTBRIDGE_TEST_ONLY";

  public static void main(String[] args) throws IOException {
    try {
      JUnit5BaseRunner runner = new JUnit5BazelRunner();

      var isBazelTestRun = isBazelTestRun();
      if (!isBazelTestRun) {
        throw new RuntimeException("Missing expected env variable in bazel test environment.");
      }

      String bazelTestSelfLocation = System.getenv(bazelEnvSelfLocation);

      // as intellij.test.jars.location value required not only here (for tests discovery) but also in other parts of the test framework
      System.setProperty("intellij.test.jars.location", Path.of(bazelTestSelfLocation).getParent().toString());

      Path bazelWorkDir = guessBazelWorkspaceDir();
      setBazelSandboxPaths(bazelWorkDir);

      System.out.println("Number of test engines: " + ServiceLoader.load(TestEngine.class).stream().count());

      TestPlan testPlan = runner.getTestPlan();
      if (testPlan.containsTests()) {
        var testExecutionListener = runner.getTestExecutionListener();
        execute(testPlan, testExecutionListener);

        if (testExecutionListener instanceof ConsoleTestLogger && ((ConsoleTestLogger)testExecutionListener).hasTestsWithThrowableResults()) {
          System.exit(1);
        }
      }
      else {
        //see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR
        System.err.println("No tests found");
        System.exit(42);
      }
    } finally {
      System.exit(0);
    }
  }

  @Override
  TestExecutionListener getTestExecutionListener() {
    if (isUnderTeamCity()) {
      return new JUnit5TeamCityRunnerForTestAllSuite.TCExecutionListener();
    } else {
      return new ConsoleTestLogger();
    }
  }

  @Override
  public Filter<?>[] getTestFilters(ClassLoader classLoader) {
    ArrayList<Filter<?>> filters = new ArrayList<>(0);
    filters.add(ClassNameFilter.includeClassNamePatterns(".*Test"));
    return filters.toArray(new Filter[0]);
  }

  @Override
  public List<? extends DiscoverySelector> getTestsSelectors(ClassLoader classLoader) {
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

    String[] parts = testFilter.split("#", 2);
    String classNamePart = parts[0];
    String className;
    if (!classNamePart.contains(".")) {
      className = findFullyQualifiedName(classNamePart, classLoader);
    }
    else {
      className = classNamePart;
    }

    if (parts.length == 2) {
      String methodName = parts[1];
      return List.of(selectMethod(classLoader, className, methodName));
    }
    else {
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

  private static Boolean isBazelTestRun() {
    return Stream.of(bazelEnvSelfLocation, bazelEnvTestTmpDir, bazelEnvRunFilesDir, bazelEnvJavaRunFilesDir)
      .allMatch(bazelTestEnv -> {
        var bazelTestEnvValue = System.getenv(bazelTestEnv);
        return bazelTestEnvValue != null && !bazelTestEnvValue.isBlank();
      });
  }

  private static void setBazelSandboxPaths(Path bazelWorkDir) throws IOException {
    setSandboxPath("idea.home.path", bazelWorkDir);
    setSandboxPath("idea.config.path", bazelWorkDir.resolve("config").resolve("test"));
    setSandboxPath("idea.system.path", bazelWorkDir.resolve("system"));
    String testUndeclaredOutputsDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    if (testUndeclaredOutputsDir != null) {
      setSandboxPath("idea.log.path", Path.of(testUndeclaredOutputsDir).resolve("logs"));
    }
    setSandboxPath("idea.log.path", bazelWorkDir.resolve("logs"));

    setSandboxPath("java.util.prefs.userRoot", bazelWorkDir.resolve("userRoot"));
    setSandboxPath("java.util.prefs.systemRoot", bazelWorkDir.resolve("systemRoot"));
  }

  private static Path guessBazelWorkspaceDir() throws IOException {
    // see https://bazel.build/concepts/dependencies#data-dependencies
    String testSrcDir = System.getenv(bazelEnvTestSrdDir);
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
