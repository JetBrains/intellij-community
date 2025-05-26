// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import org.junit.platform.engine.*;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.vintage.engine.descriptor.VintageTestDescriptor;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Used to run JUnit 5 tests via JUnit 5 runtime
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public final class JUnit5TeamCityRunnerForTestsOnClasspath {
  private static final String ourCollectTestsFile = System.getProperty("intellij.build.test.list.classes");

  public static void main(String[] args) throws IOException {
    try {
      var isBazelTestRun = isBazelTestRun();

      if (isBazelTestRun) {
        // SELF_LOCATION provides us with the path to a shell script in the directory with only the current module jars
        String bazelTestSelfLocation = System.getenv("SELF_LOCATION");
        if (bazelTestSelfLocation == null || bazelTestSelfLocation.isEmpty()) {
          throw new RuntimeException("Missing SELF_LOCATION env variable in bazel test environment");
        }
        // as intellij.test.jars.location value required not only here (for tests discovery) but also in other parts of the test framework
        System.setProperty("intellij.test.jars.location", Path.of(bazelTestSelfLocation).getParent().toString());

        Path bazelWorkDir = guessBazelWorkspaceDir();
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

      Launcher launcher = LauncherFactory.create();

      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      Set<Path> classPathRoots;
      try {
        classPathRoots = getClassPathRoots(classLoader);
      }
      catch (Throwable e) {
        e.printStackTrace();
        System.exit(1);
        return;
      }
      System.out.println("Number of test engines: " + ServiceLoader.load(TestEngine.class).stream().count());

      List<? extends DiscoverySelector> selectors;
      if (classPathRoots != null) {
        selectors = DiscoverySelectors.selectClasspathRoots(classPathRoots);
      }
      else {
        selectors = Collections.singletonList(DiscoverySelectors.selectPackage(""));
      }
      LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
        .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
        .selectors(selectors)
        .filters(getTestFilters(isBazelTestRun, classLoader)).build();
      TestPlan testPlan = launcher.discover(discoveryRequest);
      if (testPlan.containsTests()) {
        if (ourCollectTestsFile != null) {
          saveListOfTestClasses(testPlan);
          return;
        }
        var testExecutionListener = isUnderTeamCity() ? new JUnit5TeamCityRunnerForTestAllSuite.TCExecutionListener() : new ConsoleTestLogger();
        launcher.execute(testPlan, testExecutionListener);
      }
      else {
        //see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR
        System.err.println("No tests found");
        System.exit(42);
      }
    }
    finally {
      System.exit(0);
    }
  }

  private static Filter<?>[] getTestFilters(Boolean isBazelTestRun, ClassLoader classLoader) {
    ArrayList<Filter<?>> filters = new ArrayList<>(0);
    if (isBazelTestRun) {
      filters.add(ClassNameFilter.includeClassNamePatterns(".*Test"));
    } else {
      // DiscoveryRequest first filters classes by ClassNameFilter, then loads class and runs additional checks:
      // presense of annotations, test methods, etc.
      // See usages of `org.junit.platform.commons.util.ClassFilter.match(java.lang.String)`.
      // ClassNameFilter could and will be called for every class in classpath, even non-test one, even for synthetic lambda classes.
      // That's why it should be fast and should not incur additional overhead, like checking whether it belongs to the current bucket.
      ClassNameFilter nameFilter;
      // PostDiscoveryFilter runs on already discovered classes and methods (TestDescriptors), so we could run more complex checks,
      // like determining whether it belongs to the current bucket.
      PostDiscoveryFilter postDiscoveryFilter;

      try {
        nameFilter = createClassNameFilter(classLoader);
        postDiscoveryFilter = createPostDiscoveryFilter(classLoader);
      }
      catch (Throwable e) {
        e.printStackTrace();
        System.exit(1);
        return new Filter[0]; // unreachable, but javac doesn't know it.
      }
      filters.add(nameFilter);
      filters.add(postDiscoveryFilter);
      filters.add(EngineFilter.excludeEngines(VintageTestDescriptor.ENGINE_ID));
    }
    return filters.toArray(new Filter[0]);
  }

  private static Set<Path> getClassPathRoots(ClassLoader classLoader) throws Throwable {
    //noinspection unchecked
    List<Path> paths = (List<Path>)MethodHandles.publicLookup()
      .findStatic(Class.forName("com.intellij.TestAll", false, classLoader),
                  "getClassRoots", MethodType.methodType(List.class))
      .invokeExact();
    if (paths == null) return null;

    // Skip unrelated jars and any other archives, otherwise we will end up with test classes from dependencies.
    String relevantJarsRoot = System.getProperty("intellij.test.jars.location");
    return paths.stream().filter(path -> {
      return Files.isDirectory(path) ||
             (
               relevantJarsRoot != null &&
               path.getFileName().toString().endsWith(".jar") &&
               path.startsWith(relevantJarsRoot)
             );
    }).collect(Collectors.toSet());
  }

  private static ClassNameFilter createClassNameFilter(ClassLoader classLoader)
    throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
    MethodHandle included = MethodHandles.publicLookup()
      .findStatic(Class.forName("com.intellij.TestCaseLoader", true, classLoader),
                  "isClassNameIncluded", MethodType.methodType(boolean.class, String.class));
    return new ClassNameFilter() {
      @Override
      public FilterResult apply(String className) {

        try {
          if ((boolean)included.invokeExact(className)) {
            return FilterResult.included(null);
          }
          return FilterResult.excluded(null);
        }
        catch (Throwable e) {
          return FilterResult.excluded(e.getMessage());
        }
      }
    };
  }

  private static PostDiscoveryFilter createPostDiscoveryFilter(ClassLoader classLoader)
    throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
    MethodHandle included = MethodHandles.publicLookup()
      .findStatic(Class.forName("com.intellij.TestCaseLoader", true, classLoader),
                  "isClassIncluded", MethodType.methodType(boolean.class, String.class));
    return new PostDiscoveryFilter() {
      record LastCheckResult(String className, FilterResult result) {
      }

      private LastCheckResult myLastResult = null;

      private FilterResult isIncluded(String className) {
        if (myLastResult == null || !myLastResult.className.equals(className)) {
          myLastResult = new LastCheckResult(className, isIncludedImpl(className));
        }
        return myLastResult.result;
      }

      private FilterResult isIncludedImpl(String className) {
        try {
          if ((boolean)included.invokeExact(className)) {
            return FilterResult.included(null);
          }
          return FilterResult.excluded(null);
        }
        catch (Throwable e) {
          return FilterResult.excluded(e.getMessage());
        }
      }

      @Override
      public FilterResult apply(TestDescriptor descriptor) {
        if (descriptor instanceof EngineDescriptor) {
          return FilterResult.included(null);
        }
        TestSource source = descriptor.getSource().orElse(null);
        if (source == null) {
          return FilterResult.included("No source for descriptor");
        }
        if (source instanceof MethodSource methodSource) {
          return isIncluded(methodSource.getClassName());
        }
        if (source instanceof ClassSource classSource) {
          return isIncluded(classSource.getClassName());
        }
        return FilterResult.included("Unknown source type " + source.getClass());
      }
    };
  }

  private static void saveListOfTestClasses(TestPlan testPlan) {
    ArrayList<String> testClasses = new ArrayList<>(0);
    for (TestIdentifier root : testPlan.getRoots()) {
      Set<TestIdentifier> firstLevel = testPlan.getChildren(root);
      for (TestIdentifier identifier : firstLevel) {
        identifier.getSource()
          .filter(source -> source instanceof ClassSource)
          .map(source -> ((ClassSource)source).getClassName())
          .ifPresent(name -> testClasses.add(name));
      }
    }
    Path path = Path.of(ourCollectTestsFile);
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, testClasses);
    }
    catch (IOException e) {
      System.err.printf("Cannot save list of test classes to '%s': %s%n", path.toAbsolutePath(), e);
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static boolean isUnderTeamCity() {
    var teamCityVersion = System.getenv("TEAMCITY_VERSION");
    return teamCityVersion != null && !teamCityVersion.isEmpty();
  }

  // bazel-specific
  private static Path guessBazelWorkspaceDir() throws IOException {
    // see https://bazel.build/concepts/dependencies#data-dependencies
    String testSrcDir = System.getenv("TEST_SRCDIR");
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

  private static Boolean isBazelTestRun() {
    return Stream.of("TEST_TMPDIR", "RUNFILES_DIR", "JAVA_RUNFILES").allMatch( bazelTestEnv -> System.getenv(bazelTestEnv) != null);
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
        System.out.println("Finished: " + testIdentifier.getDisplayName() + " -> " + result.getStatus());
        result.getThrowable().ifPresent(t -> t.printStackTrace(System.out));
      }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
      System.out.println("Test plan finished with " + testPlan.countTestIdentifiers(TestIdentifier::isTest) + " tests.");
    }
  }
}
