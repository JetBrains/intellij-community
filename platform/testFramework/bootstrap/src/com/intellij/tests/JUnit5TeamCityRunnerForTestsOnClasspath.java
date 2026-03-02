// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import jetbrains.buildServer.messages.serviceMessages.TestFinished;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.vintage.engine.descriptor.VintageTestDescriptor;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

// Used to run JUnit 5 tests via JUnit 5 runtime
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public final class JUnit5TeamCityRunnerForTestsOnClasspath {
  static final String LIST_CLASSES = System.getProperty("intellij.build.test.list.classes");

  static void assertNoUnhandledExceptions(String kind, Throwable e) {
    String runConfigurationName = System.getProperty("intellij.build.run.configuration.name");
    final String testName =
      kind + ".assertNoUnhandledExceptions" + (runConfigurationName == null ? "" : ("(" + runConfigurationName + ")"));

    System.out.println(new TestStarted(testName, true, null));
    if (e != null) {
      var testFailedServiceMessage = new TestFailed(testName, e).toString();
      if (!isLeak(testFailedServiceMessage)) {
        // leaks are already checked by _LastInSuiteTest.testProjectLeak
        System.out.println(testFailedServiceMessage);
      }
    }
    System.out.println(new TestFinished(testName, 0));
  }

  public static void main(String[] args) {
    JUnit5TeamCityRunner.TCExecutionListener listener = null;
    Throwable caughtException = null;
    boolean noTestsFound = false;

    try {
      Launcher launcher = LauncherFactory.create();

      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      // DiscoveryRequest first filters classes by ClassNameFilter, then loads class and runs additional checks:
      // presense of annotations, test methods, etc.
      // See usages of `org.junit.platform.commons.util.ClassFilter.match(java.lang.String)`.
      // ClassNameFilter could and will be called for every class in classpath, even non-test one, even for synthetic lambda classes.
      // That's why it should be fast and should not incur additional overhead, like checking whether it belongs to the current bucket.
      ClassNameFilter nameFilter;
      // PostDiscoveryFilter runs on already discovered classes and methods (TestDescriptors), so we could run more complex checks,
      // like determining whether it belongs to the current bucket.
      PostDiscoveryFilter postDiscoveryFilter;
      PostDiscoveryFilter performancePostDiscoveryFilter;
      Set<Path> classPathRoots;
      try {
        nameFilter = createClassNameFilter(classLoader);
        postDiscoveryFilter = createPostDiscoveryFilter(classLoader);
        performancePostDiscoveryFilter = new JUnit5TeamCityRunner.PerformancePostDiscoveryFilter();
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
        .filters(nameFilter, postDiscoveryFilter, performancePostDiscoveryFilter, EngineFilter.excludeEngines(VintageTestDescriptor.ENGINE_ID)).build();
      TestPlan testPlan = launcher.discover(discoveryRequest);
      if (testPlan.containsTests()) {
        if (LIST_CLASSES != null) {
          saveListOfTestClasses(testPlan);
          return;
        }
        listener = new JUnit5TeamCityRunner.TCExecutionListener();
        launcher.execute(testPlan, listener);
      }
      else {
        noTestsFound = true;
      }
    }
    catch (Throwable e) {
      caughtException = e;
    }
    finally {
      assertNoUnhandledExceptions("JUnit5TeamCityRunnerForTestsOnClasspath", caughtException);
    }

    // Determine exit code OUTSIDE of try/catch/finally to avoid finally overriding the exit code
    int exitCode;
    if (caughtException != null) {
      exitCode = 1;
    }
    else if (noTestsFound || !listener.smthExecuted()) {
      // see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR
      exitCode = 42;
    }
    else if (listener.hasFailures()) {
      exitCode = 1;
    }
    else {
      exitCode = 0;
    }

    System.exit(exitCode);
  }

  static Set<Path> getClassPathRoots(ClassLoader classLoader) throws Throwable {
    //noinspection unchecked
    List<Path> paths = (List<Path>)MethodHandles.publicLookup()
      .findStatic(Class.forName("com.intellij.TestAll", false, classLoader),
                  "getClassRoots", MethodType.methodType(List.class))
      .invokeExact();
    if (paths == null) return null;
    // Skip unrelated jars and any other archives, otherwise we will end up with test classes from dependencies.
    String relevantJarsRoot = System.getProperty("intellij.test.jars.location");
    if (relevantJarsRoot == null) {
      String bazelOutPattern = Paths.get("bazel-out", "jvm-fastbuild").toString();
      String jar = paths.stream().map(Path::toString).filter(s -> s.contains(bazelOutPattern)).findFirst().orElse(null);
      int index = jar != null ? jar.indexOf(bazelOutPattern) : -1;
      if (index != -1) {
        relevantJarsRoot = jar.substring(0, index + bazelOutPattern.length());
      }
    }
    String finalRelevantJarsRoot = relevantJarsRoot;
    return paths.stream()
      .filter(path ->
                Files.isDirectory(path) ||
                (finalRelevantJarsRoot != null && path.getFileName().toString().endsWith(".jar") && path.startsWith(finalRelevantJarsRoot)))
      .collect(Collectors.toSet());
  }

  static ClassNameFilter createClassNameFilter(ClassLoader classLoader)
    throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
    MethodHandle included = MethodHandles.publicLookup()
      .findStatic(Class.forName("com.intellij.TestCaseLoader", true, classLoader),
                  "isClassNameIncluded", MethodType.methodType(boolean.class, String.class));
    try {
      boolean ignored = (boolean)included.invokeExact(Object.class.getName() + "Test");  // force load test classes filter, *Test matches ClassFinder#isSuitableTestClassName
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
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

  static PostDiscoveryFilter createPostDiscoveryFilter(ClassLoader classLoader)
    throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
    MethodHandle included = MethodHandles.publicLookup()
      .findStatic(Class.forName("com.intellij.TestCaseLoader", true, classLoader),
                  "isClassIncluded", MethodType.methodType(boolean.class, Class.class));
    try {
      boolean ignored = (boolean)included.invokeExact(Object.class);  // force load bucketing scheme
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
    return new PostDiscoveryFilter() {
      record LastCheckResult(String className, FilterResult result) {
      }

      private LastCheckResult myLastResult = null;

      private FilterResult isIncluded(Class<?> aClass) {
        if (myLastResult == null || !myLastResult.className.equals(aClass.getName())) {
          myLastResult = new LastCheckResult(aClass.getName(), isIncludedImpl(aClass));
        }
        return myLastResult.result;
      }

      private FilterResult isIncludedImpl(Class<?> aClass) {
        try {
          if ((boolean)included.invokeExact(aClass)) {
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
          return isIncluded(methodSource.getJavaClass());
        }
        if (source instanceof ClassSource classSource) {
          return isIncluded(classSource.getJavaClass());
        }
        return FilterResult.included("Unknown source type " + source.getClass());
      }
    };
  }

  static void saveListOfTestClasses(TestPlan testPlan) {
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
    Path path = Path.of(LIST_CLASSES);
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

  private static boolean isLeak(String testFailedServiceMessage) {
    return
      // copied from com.intellij.testFramework.LeakHunter#getLeakedObjectDetails
      testFailedServiceMessage.contains("Found a leaked instance of") ||
      // copied from com.intellij.openapi.util.ObjectNode#assertNoChildren
      testFailedServiceMessage.contains("Memory leak detected") &&
      testFailedServiceMessage.contains("was registered in Disposer");
  }
}
