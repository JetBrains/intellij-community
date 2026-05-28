// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import jetbrains.buildServer.messages.serviceMessages.TestFinished;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import jetbrains.buildServer.messages.serviceMessages.TestStdOut;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted;
import junit.framework.TestSuite;
import org.junit.platform.commons.logging.LogRecordListener;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.vintage.engine.VintageTestEngine;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Runs JUnit 3/4 tests using {@linkplain VintageTestEngine}, or JUnit 5 tests using {@linkplain JupiterTestEngine}.
 *
 * Supported options:
 * - __class__ className[;...]
 * - __package__ packageName
 * - __classpathroot__
 *   if {@systemProperty intellij.build.test.reverse.order} is set, tests are run in reverse order
 * - className
 * - className methodName
 * if {@systemProperty intellij.build.test.list.classes} is set, only test discovery is performed and the resulting list of test classes is saved to the file specified by this property
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class JUnit5TeamCityRunner {
  private static final String IGNORE_FIRST_AND_LAST_TESTS = System.getProperty("intellij.build.test.ignoreFirstAndLastTests");
  private static final String LIST_CLASSES = System.getProperty("intellij.build.test.list.classes");
  private static final String REVERSE_ORDER = System.getProperty("intellij.build.test.reverse.order");
  private static final String INCLUDE_TAGS = System.getProperty("intellij.build.test.tags");
  private static final String EXCLUDE_TAGS = System.getProperty("intellij.build.test.excluded.tags");

  static boolean isUnderTeamCity() {
    var teamCityVersion = System.getenv("TEAMCITY_VERSION");
    return teamCityVersion != null && !teamCityVersion.isEmpty();
  }

  public static void main(String[] args) throws ClassNotFoundException {
    if (args.length != 1 && args.length != 2) {
      System.err.printf("Expected one or two arguments, got %d: %s%n", args.length, Arrays.toString(args));
      System.exit(1);
    }

    TestExecutionListenerEx listener = null;
    Throwable caughtException = null;

    try {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      List<? extends DiscoverySelector> selectors;
      List<Filter<?>> filters = new ArrayList<>(0);

      if (args[0].equals("__class__")) {
        String[] classNames = args[1].split(";");
        selectors = Arrays.stream(classNames).map(className -> {
          String[] parts = className.split("#", 2);
          return parts.length == 1 ? DiscoverySelectors.selectClass(className) : DiscoverySelectors.selectMethod(parts[0], parts[1]);
        }).toList();
        // no filters
      }
      else if (args[0].equals("__package__")) {
        String packageName = args[1];
        selectors = Collections.singletonList(DiscoverySelectors.selectPackage(packageName));
        // exclude subpackages
        filters.add(ClassNameFilter.excludeClassNamePatterns("\\Q" + args[1] + "\\E\\.[^.]+\\..*"));
      }
      else if (args[0].equals("__classpathroot__")) {
        Set<Path> classpathRoots = getClassRoots(classLoader);
        if (classpathRoots == null) throw new RuntimeException("Failed to get classpath roots");
        selectors = DiscoverySelectors.selectClasspathRoots(classpathRoots);
        filters.add(new CommonTestClassesFilter());         // name check
        filters.add(new BucketingClassNameFilter());        // bucketing
        filters.add(new IgnorePostDiscoveryFilter());       // IJIgnore and Ignore support in JUnit 3/4
        filters.add(new PerformancePostDiscoveryFilter());  // PerformanceUnitTest support
        filters.add(new HeadlessPostDiscoveryFilter());     // SkipInHeadlessEnvironment support in JUnit 3/4
        if (INCLUDE_TAGS != null) filters.add(TagFilter.includeTags(INCLUDE_TAGS.split(";")));        // JUnit 5 tag filter
        if (EXCLUDE_TAGS != null) filters.add(TagFilter.excludeTags(EXCLUDE_TAGS.split(";")));        // JUnit 5 tag exclusion filter
      }
      else if (args.length == 1) {
        String className = args[0];
        selectors = Collections.singletonList(DiscoverySelectors.selectClass(className));
        filters.addAll(List.of(new IgnorePostDiscoveryFilter(), new PerformancePostDiscoveryFilter()));  // filter methods
      }
      else {
        String className = args[0];
        String methodName = args[1];
        selectors = Collections.singletonList(DiscoverySelectors.selectMethod(className, methodName));
        // no filters
      }

      LoggerFactory.addListener(new TCLogRecordListener());  // report warnings/errors as build problems on TeamCity, incl. test discovery

      Launcher launcher = LauncherFactory.create();
      LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectors)
        .filters(filters.toArray(Filter[]::new))
        .build();
      TestPlan testPlan = launcher.discover(discoveryRequest);

      listener = isUnderTeamCity() ? new TCExecutionListener() : new ConsoleTestExecutionListener();

      if (LIST_CLASSES != null) {
        saveListOfTestClasses(testPlan);  // save only
      }
      else if ("true".equals(REVERSE_ORDER)) {
        executeInReverseOrder(launcher, testPlan, listener, filters);  // used by [IDEA Trunk / Tests / Linux x86_64 Reversed Order / Aggregator](https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_Idea_Tests_AggregatorRevX64)
        listener.finalizeOutput();
      }
      else {
        launcher.execute(testPlan, listener);
        listener.finalizeOutput();
      }
    }
    catch (Throwable e) {
      caughtException = e;
    }
    finally {
      assertNoUnhandledExceptions(caughtException);
    }

    // Determine exit code OUTSIDE of try/catch/finally to avoid finally overriding the exit code
    int exitCode;
    if (caughtException != null || listener.hasFailures()) {
      // see org.jetbrains.intellij.build.impl.TestingTasksImpl.EXIT_FAILURE
      exitCode = 41;
    }
    else if (!listener.smthExecuted()) {
      // see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR
      exitCode = 42;
    }
    else {
      exitCode = 0;
    }

    System.exit(exitCode);
  }

  private static void assertNoUnhandledExceptions(Throwable e) {
    String testProcessName = System.getProperty("intellij.build.test.process.name", "");
    if (!testProcessName.isEmpty()) testProcessName = "(" + testProcessName + ")";
    String buildConfName = System.getProperty("teamcity.buildConfName", "");
    if (!buildConfName.isEmpty()) buildConfName = "[" + buildConfName + "]";
    final String testName = "JUnit5TeamCityRunner.assertNoUnhandledExceptions" + testProcessName + buildConfName;

    if (isUnderTeamCity()) {
      System.out.println(new TestStarted(testName, true, null));
    }
    if (e != null) {
      var testFailedServiceMessage = new TestFailed(testName, e).toString();
      if (assertNoUnhandledExceptions_isLeak(testFailedServiceMessage) && !"true".equals(IGNORE_FIRST_AND_LAST_TESTS)) {
        // leaks are already checked by _LastInSuiteTest.testProjectLeak, ignore
      }
      else {
        if (isUnderTeamCity()) {
          System.out.println(testFailedServiceMessage);
        } else {
          System.out.println("Unhandled exception in runner: " + e);
        }
      }
    }
    if (isUnderTeamCity()) {
      System.out.println(new TestFinished(testName, 0));
    }
  }

  private static boolean assertNoUnhandledExceptions_isLeak(String testFailedServiceMessage) {
    return
      // copied from com.intellij.testFramework.LeakHunter#getLeakedObjectDetails
      testFailedServiceMessage.contains("Found a leaked instance of") ||
      // copied from com.intellij.openapi.util.ObjectNode#assertNoChildren
      testFailedServiceMessage.contains("Memory leak detected") &&
      testFailedServiceMessage.contains("was registered in Disposer");
  }

  private static Set<Path> getClassRoots(ClassLoader classLoader) throws Throwable {
    //noinspection unchecked
    List<Path> testRoots = (List<Path>)MethodHandles.publicLookup()
      .findStatic(Class.forName("com.intellij.TestCaseLoader", false, classLoader),
                  "getClassRoots", MethodType.methodType(List.class))
      .invokeExact();
    return new HashSet<>(testRoots);
  }

  private static void executeInReverseOrder(Launcher launcher, TestPlan testPlan, TestExecutionListener listener, List<Filter<?>> filters) {
    List<? extends DiscoverySelector> selectors = testPlan.getRoots().stream().flatMap(root -> {  // keep engine order
      List<TestIdentifier> children = new ArrayList<>(testPlan.getChildren(root));
      Collections.reverse(children);
      return children.stream().map(child -> DiscoverySelectors.selectUniqueId(child.getUniqueIdObject()));
    }).toList();

    LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
      .selectors(selectors)
      .filters(filters.toArray(Filter[]::new))
      .build();
    launcher.execute(discoveryRequest, listener);
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
    Path path = Path.of(LIST_CLASSES);
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, testClasses);
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot save list of test classes to " + path.toAbsolutePath(), e);
    }
  }

  public static class TCLogRecordListener extends LogRecordListener {
    private static final List<String> KNOWN_EXCEPTIONAL_WARNINGS = List.of(
      "Deleting symbolic link from location inside of temp dir (",  // https://youtrack.jetbrains.com/issue/AT-4053
      "Discovered 2 'junit-platform.properties' configuration files on the classpath (see below); only the first (*) will be used.",  // https://github.com/junit-team/junit-framework/issues/2794, https://youtrack.jetbrains.com/issue/AT-4058
      "Discovered 3 'junit-platform.properties' configuration files on the classpath (see below); only the first (*) will be used.",
      "Discovered 4 'junit-platform.properties' configuration files on the classpath (see below); only the first (*) will be used.",
      "Failed to invoke TestWatcher [com.intellij.ide.starter.junit5.JUnit5TestWatcher] for method [com.intellij.workspaceModel.integrationTests.tests.aggregator.maven.",  // https://youtrack.jetbrains.com/issue/AT-4052
      "TestExecutionListener [com.intellij.ide.starter.junit5.FreeSpacePrinter] threw exception for method: executionStarted(TestIdentifier [uniqueId = [engine:group-by-mode]/[class:com.jetbrains.rdct.lambdaTestsUi.UiInfrastructureTest]/",  // https://youtrack.jetbrains.com/issue/AT-4051
      "Type implements CloseableResource but not AutoCloseable: org.testcontainers.junit.jupiter.TestcontainersExtension$StoreAdapter"  // https://github.com/testcontainers/testcontainers-java/issues/10525
    );

    @Override
    public void logRecordSubmitted(LogRecord r) {
      if (r.getLevel() == Level.WARNING && KNOWN_EXCEPTIONAL_WARNINGS.stream().noneMatch(w -> r.getMessage().startsWith(w)) ||
          r.getLevel() == Level.SEVERE) {
        if (isUnderTeamCity()) {
          System.out.println(ServiceMessage.asString(ServiceMessageTypes.BUILD_PROBLEM, Map.of("description", r.getMessage())));
        }
        else {
          System.out.println("[" + r.getLevel() + "] " + r.getMessage());
        }
      }
    }
  }

  public interface TestExecutionListenerEx extends TestExecutionListener {
    boolean smthExecuted();
    boolean hasFailures();
    default void finalizeOutput() { }
  }

  private static class ConsoleTestExecutionListener implements TestExecutionListenerEx {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    private static final int MAX_STACKTRACE_MESSAGE_LENGTH =
      Integer.getInteger("intellij.build.test.stacktrace.max.length", 100 * 1024);

    private boolean mySomethingExecuted;
    private final List<String> myFailedPaths = new ArrayList<>();
    private int myFailed;
    private int myPassed;
    private int mySkipped;
    private int myTotal;
    private long myPlanStartNanos;

    private static void printStacktrace(Throwable throwable) {
      String trace = TCExecutionListener.getTrace(throwable, MAX_STACKTRACE_MESSAGE_LENGTH);
      for (String line : trace.split("\n", -1)) {
        if (line.isEmpty()) continue;
        System.out.println("\t" + line.trim());
      }
    }

    private static String colored(String text, String ansiColor) {
      return ansiColor != null ? ansiColor + text + ANSI_RESET : text;
    }

    private static String displayName(TestIdentifier id) {
      String displayName = id.getDisplayName();

      return id.getSource()
        .map(source -> switch (source) {
          case ClassSource classSource -> classSource.getClassName();
          case MethodSource methodSource -> methodSource.getClassName() + "#" + methodSource.getMethodName();
          default -> displayName;
        })
        .orElse(displayName);
    }

    private static String escapeNewlines(String s) {
      if (s == null) return "null";
      return s.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n");
    }


    @Override
    public boolean smthExecuted() {
      return mySomethingExecuted;
    }

    @Override
    public boolean hasFailures() {
      return myFailed > 0;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
      myPlanStartNanos = System.nanoTime();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
      if (!testIdentifier.isTest()) return;
      mySomethingExecuted = true;
      System.out.println(displayName(testIdentifier) + " :: " + colored("STARTED", null));
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
      mySomethingExecuted = true;
      myTotal++;
      mySkipped++;
      System.out.println(displayName(testIdentifier) + " :: " + colored("SKIPPED", ANSI_YELLOW));
      if (reason != null && !reason.isEmpty()) {
        System.out.println("\tReason: " + escapeNewlines(reason));
      }
    }

    private void printFinished(TestIdentifier testIdentifier, String status, Throwable throwable) {
      System.out.println(displayName(testIdentifier) + " :: " + status);
      if (throwable != null) {
        printStacktrace(throwable);
      }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
      TestExecutionResult.Status status = testExecutionResult.getStatus();
      Throwable throwable = testExecutionResult.getThrowable().orElse(null);
      if (testIdentifier.isTest()) {
        switch (status) {
          case SUCCESSFUL -> {
            printFinished(testIdentifier, colored("SUCCESSFUL", ANSI_GREEN), null);
            myTotal++;
            myPassed++;
          }
          case FAILED -> {
            String path = displayName(testIdentifier);
            printFinished(testIdentifier, colored("FAILED", ANSI_RED), throwable);
            myTotal++;
            myFailed++;
            myFailedPaths.add(path);
          }
          case ABORTED -> {
            printFinished(testIdentifier, colored("ABORTED", ANSI_YELLOW), throwable);
            myTotal++;
            mySkipped++;
          }
        }
        return;
      }
      if (throwable == null) return;
      if (status == TestExecutionResult.Status.FAILED) {
        String syntheticPath = displayName(testIdentifier) + " > <classInitializationError>";
        System.out.println(syntheticPath + " :: " + colored("FAILED", ANSI_RED));
        printStacktrace(throwable);
        mySomethingExecuted = true;
        myTotal++;
        myFailed++;
        myFailedPaths.add(syntheticPath);
      }
      else if (status == TestExecutionResult.Status.ABORTED) {
        String syntheticPath = displayName(testIdentifier) + " > <classInitializationSkipped>";
        String message = throwable.getMessage();
        System.out.println(syntheticPath + " :: " + colored("SKIPPED", ANSI_YELLOW));
        if (message != null && !message.isEmpty()) {
          System.out.println("\tReason: " + escapeNewlines(message));
        }
        mySomethingExecuted = true;
        myTotal++;
        mySkipped++;
      }
    }

    @Override
    public void finalizeOutput() {
      if (myPlanStartNanos == 0L && myTotal == 0) return;
      String line = "====================================================";
      String durationSuffix = myPlanStartNanos != 0L
                              ? " (" + ((System.nanoTime() - myPlanStartNanos) / 1_000_000L) + " ms)"
                              : "";
      System.out.println();
      System.out.println(line);
      System.out.println("Results: " + myTotal + (myTotal == 1 ? " test, " : " tests, ") +
                    colored(myPassed + " passed", ANSI_GREEN) + ", " +
                    colored(myFailed + " failed", myFailed > 0 ? ANSI_RED : null) + ", " +
                    colored(mySkipped + " skipped", mySkipped > 0 ? ANSI_YELLOW : null) +
                    durationSuffix);
      if (!myFailedPaths.isEmpty()) {
        System.out.println();
        System.out.println(colored("Failed tests:", ANSI_RED));
        for (String path : myFailedPaths) {
          System.out.println("  " + path);
        }
      }
      System.out.println(line);
      System.out.flush();
    }
  }

  public static class TCExecutionListener implements TestExecutionListenerEx {
    /**
     * The same constant as com.intellij.rt.execution.TestListenerProtocol.CLASS_CONFIGURATION
     */
    private static final String CLASS_CONFIGURATION = "Class Configuration";
    private final PrintStream myPrintStream;

    private TestPlan myTestPlan;
    private long myCurrentTestStart = 0;
    private int myFinishCount = 0;
    private boolean myHasFailures = false;
    private static final int MAX_STACKTRACE_MESSAGE_LENGTH =
      Integer.getInteger("intellij.build.test.stacktrace.max.length", 100 * 1024);

    /**
     * If true, then all the standard output and standard error messages
     * received between testStarted and testFinished messages will be considered test output.
     * @see <a href="https://www.jetbrains.com/help/teamcity/service-messages.html#Nested+Test+Reporting">TeamCity test reporting</a>
     */
    private static final boolean CAPTURE_STANDARD_OUTPUT;

    static {
      if ("false".equalsIgnoreCase(System.getProperty("intellij.build.test.captureStandardOutput"))) {
        CAPTURE_STANDARD_OUTPUT = false;
      }
      else {
        CAPTURE_STANDARD_OUTPUT = true;
      }
    }

    private TCExecutionListener() {
      myPrintStream = System.out;
      myPrintStream.println("##teamcity[enteredTheMatrix]");
    }

    @Override
    public boolean smthExecuted() {
      return myCurrentTestStart != 0;
    }

    @Override
    public boolean hasFailures() {
      return myHasFailures;
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
      StringBuilder builder = new StringBuilder();
      builder.append("timestamp = ").append(entry.getTimestamp());
      entry.getKeyValuePairs().forEach((key, value) -> builder.append(", ").append(key).append(" = ").append(value));
      builder.append("\n");
      myPrintStream.println(new TestStdOut(getName(testIdentifier), builder.toString()));
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
      myTestPlan = testPlan;
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
      executionStarted(testIdentifier);
      executionFinished(testIdentifier, TestExecutionResult.Status.ABORTED, null, reason);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
      if (testIdentifier.isTest()) {
        testStarted(testIdentifier);
        myCurrentTestStart = System.nanoTime();
      }
      else if (hasNonTrivialParent(testIdentifier)) {
        myFinishCount = 0;
        if (shouldReportAsTestSuite(testIdentifier)) myPrintStream.println(new TestSuiteStarted(getName(testIdentifier)));
      }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
      final Throwable throwable = testExecutionResult.getThrowable().orElse(null);
      if (throwable != null && IgnoreException.isIgnoringThrowable(throwable)) {
        String message = throwable.getMessage();
        executionFinished(testIdentifier, TestExecutionResult.Status.ABORTED, null, message != null ? message : "");
      }
      else if (Retries.NUMBER > 0 && testIdentifier.isTest() && Retries.getAndClearSuccessfulStatus(testIdentifier)) {
        executionFinished(testIdentifier, TestExecutionResult.Status.SUCCESSFUL, null, null);
      }
      else {
        executionFinished(testIdentifier, testExecutionResult.getStatus(), throwable, null);
      }
    }

    private void executionFinished(TestIdentifier testIdentifier,
                                   TestExecutionResult.Status status,
                                   Throwable throwableOptional,
                                   String reason) {
      // Track failures for exit code determination
      if (status == TestExecutionResult.Status.FAILED) {
        myHasFailures = true;
      }

      if (testIdentifier.isTest()) {
        final long duration = getDuration();
        if (status == TestExecutionResult.Status.FAILED) {
          testFailure(testIdentifier, ServiceMessageTypes.TEST_FAILED, throwableOptional, duration, reason);
        }
        else if (status == TestExecutionResult.Status.ABORTED) {
          testFailure(testIdentifier, ServiceMessageTypes.TEST_IGNORED, throwableOptional, duration, reason);
        }

        TestLocationStorage.recordTestLocation(testIdentifier, status, getTestNameForMetadata(testIdentifier));

        testFinished(testIdentifier, duration);
        myFinishCount++;
      }
      else if (hasNonTrivialParent(testIdentifier)) {
        final boolean shouldReportAsTestSuite = shouldReportAsTestSuite(testIdentifier);
        if (status == TestExecutionResult.Status.FAILED) {
          if (!shouldReportAsTestSuite) myPrintStream.println(new TestSuiteStarted(getName(testIdentifier)));
          myPrintStream.println(new TestStarted(CLASS_CONFIGURATION, false, null));
          testFailure(CLASS_CONFIGURATION, ServiceMessageTypes.TEST_FAILED, throwableOptional, 0, reason);
          myPrintStream.println(new TestFinished(CLASS_CONFIGURATION, 0));
          if (!shouldReportAsTestSuite) myPrintStream.println(new TestSuiteFinished(getName(testIdentifier)));
        }
        if (status != TestExecutionResult.Status.SUCCESSFUL) {
          final Set<TestIdentifier> descendants = myTestPlan != null ? myTestPlan.getDescendants(testIdentifier) : Collections.emptySet();
          if (!descendants.isEmpty() && myFinishCount == 0) {
            for (TestIdentifier childIdentifier : descendants) {
              testStarted(childIdentifier);
              testFailure(childIdentifier, ServiceMessageTypes.TEST_IGNORED,
                          status == TestExecutionResult.Status.ABORTED ? throwableOptional : null, 0, reason);
              testFinished(childIdentifier, 0);
            }
            myFinishCount = 0;
          }
        }
        if (shouldReportAsTestSuite) myPrintStream.println(new TestSuiteFinished(getName(testIdentifier)));
        if (status == TestExecutionResult.Status.ABORTED) myCurrentTestStart = 1;  // mark ignored classes as #smthExecuted
      }
      else {
        if (status == TestExecutionResult.Status.FAILED) {
          String testProcessName = System.getProperty("intellij.build.test.process.name", "");
          if (!testProcessName.isEmpty()) testProcessName = "(" + testProcessName + ")";
          String buildConfName = System.getProperty("teamcity.buildConfName", "");
          if (!buildConfName.isEmpty()) buildConfName = "[" + buildConfName + "]";
          final String testName = CLASS_CONFIGURATION + testProcessName + buildConfName;
          myPrintStream.println(new TestSuiteStarted(getName(testIdentifier)));  // root (e.g. JupiterTestEngine)
          myPrintStream.println(new TestStarted(testName, false, null));
          testFailure(testName, ServiceMessageTypes.TEST_FAILED, throwableOptional, 0, reason);
          myPrintStream.println(new TestFinished(testName, 0));
          myPrintStream.println(new TestSuiteFinished(getName(testIdentifier)));
        }
      }
    }

    private static boolean hasNonTrivialParent(TestIdentifier testIdentifier) {
      return testIdentifier.getParentId().isPresent();
    }

    private static boolean shouldReportAsTestSuite(TestIdentifier testIdentifier) {
      if (!(testIdentifier.getSource().orElse(null) instanceof ClassSource classSource)) return false;
      Class<?> aClass = classSource.getJavaClass();

      // JUnit 3
      if (TestSuite.class.isAssignableFrom(aClass)) return true;

      // JUnit 4
      RunWith runWith = aClass.getAnnotation(RunWith.class);
      if (runWith != null && Suite.class.isAssignableFrom(runWith.value())) return true;

      // JUnit 5
      return Arrays.stream(aClass.getAnnotations()).anyMatch(a -> "org.junit.platform.suite.api.Suite".equals(a.annotationType().getName()));
    }

    protected long getDuration() {
      return (System.nanoTime() - myCurrentTestStart) / 1_000_000;
    }

    private void testStarted(TestIdentifier testIdentifier) {
      myPrintStream.println(new TestStarted(getName(testIdentifier), CAPTURE_STANDARD_OUTPUT, null));
    }

    private void testFinished(TestIdentifier testIdentifier, long duration) {
      myPrintStream.println(new TestFinished(getName(testIdentifier), (int)duration));
    }

    private void testFailure(TestIdentifier testIdentifier,
                             String messageName,
                             Throwable ex,
                             long duration,
                             String reason) {
      testFailure(getName(testIdentifier), messageName, ex, duration, reason);
    }

    private static String getName(TestIdentifier testIdentifier) {
      String displayName = testIdentifier.getDisplayName();
      return testIdentifier.getSource()
        .map(s -> {
          if (s instanceof ClassSource) {
            String className = ((ClassSource)s).getClassName();
            if (className.equals(TestSuite.class.getName()) || className.equals(displayName)) {
              //class level failure
              return displayName;
            }
            String withDisplayName = "." + displayName;
            return className.endsWith(withDisplayName) ? className
                                                       : className + withDisplayName;
          }
          if (s instanceof MethodSource) {
            String className = ((MethodSource)s).getClassName();
            String methodName = ((MethodSource)s).getMethodName();
            return displayName.startsWith(methodName) ? className + "." + displayName
                                                      : className + "." + methodName + "[" + displayName + "]";
          }
          return null;
        }).orElse(displayName);
    }

    private void testFailure(String methodName,
                             String messageName,
                             Throwable ex,
                             long duration,
                             String reason) {
      final Map<String, String> attrs = new LinkedHashMap<>();
      try {
        attrs.put("name", methodName);
        if (duration > 0) {
          attrs.put("duration", Long.toString(duration));
        }
        if (ex != null) {
          attrs.put("message", limit(ex.toString()));  // if empty reason
          attrs.put("details", getTrace(ex, MAX_STACKTRACE_MESSAGE_LENGTH));
        }
        if (reason != null) {
          attrs.put("message", limit(reason));
        }
        if (ex != null) {
          if (ex instanceof MultipleFailuresError && ((MultipleFailuresError)ex).hasFailures()) {
            for (Throwable assertionError : ((MultipleFailuresError)ex).getFailures()) {
              testFailure(methodName, messageName, assertionError, duration, reason);
            }
          }
          else if (ex instanceof AssertionFailedError &&
                   ((AssertionFailedError)ex).isActualDefined() &&
                   ((AssertionFailedError)ex).isExpectedDefined()) {
            attrs.put("expected", limit(((AssertionFailedError)ex).getExpected().getStringRepresentation()));
            attrs.put("actual", limit(((AssertionFailedError)ex).getActual().getStringRepresentation()));
            attrs.put("type", "comparisonFailure");
          }
          else {
            Class<? extends Throwable> aClass = ex.getClass();
            if (isComparisonFailure(aClass)) {
              try {
                String expected = (String)aClass.getMethod("getExpected").invoke(ex);
                String actual = (String)aClass.getMethod("getActual").invoke(ex);

                attrs.put("expected", limit(expected));
                attrs.put("actual", limit(actual));
                attrs.put("type", "comparisonFailure");
              }
              catch (Throwable e) {
                e.printStackTrace(myPrintStream);
              }
            }
          }
        }
      }
      finally {
        myPrintStream.println(ServiceMessage.asString(messageName, attrs));
      }
    }

    private static boolean isComparisonFailure(Class<?> aClass) {
      if (aClass == null) return false;
      final String throwableClassName = aClass.getName();
      if (throwableClassName.equals("junit.framework.ComparisonFailure") ||
          throwableClassName.equals("org.junit.ComparisonFailure")) {
        return true;
      }
      return isComparisonFailure(aClass.getSuperclass());
    }

    private static String limit(String string) {
      if (string == null) return null;
      if (string.length() > MAX_STACKTRACE_MESSAGE_LENGTH) {
        return string.substring(0, MAX_STACKTRACE_MESSAGE_LENGTH);
      }
      return string;
    }

    public static String getTrace(Throwable ex, int limit) {
      final StringWriter stringWriter = new StringWriter();
      final LimitedStackTracePrintWriter writer = new LimitedStackTracePrintWriter(stringWriter, limit);
      ex.printStackTrace(writer);
      writer.close();
      return stringWriter.toString();
    }

    /**
     * Required for TC to match parametrized and factory tests when we attach metadata after the run
     */
    private String getFullTestPath(TestIdentifier testIdentifier) {
      List<String> names = new ArrayList<>();
      Optional<TestIdentifier> parent = myTestPlan.getParent(testIdentifier);
      boolean isImmediateParent = true;

      while (parent.isPresent()) {
        TestIdentifier p = parent.get();
        if (hasNonTrivialParent(p)) {
          // Skip class-level parent only if it's the immediate parent of a method test
          // (getName already includes the class name)
          boolean skipClassParent = isImmediateParent
                                    && p.getSource().orElse(null) instanceof ClassSource cs
                                    && testIdentifier.getSource().orElse(null) instanceof MethodSource ms
                                    && cs.getClassName().equals(ms.getClassName());

          if (!skipClassParent) {
            names.add(p.getSource().map(s -> switch (s) {
              case ClassSource source -> source.getClassName();
              case MethodSource ms -> ms.getClassName() + "." + p.getDisplayName();
              default -> p.getDisplayName();
            }).orElse(p.getDisplayName()));
          }
        }
        parent = myTestPlan.getParent(p);
        isImmediateParent = false;
      }

      Collections.reverse(names);
      names.add(getName(testIdentifier));
      return String.join(": ", names);
    }

    private String getTestNameForMetadata(TestIdentifier testIdentifier) {
      boolean parentIsMethodSource = myTestPlan.getParent(testIdentifier)
        .flatMap(TestIdentifier::getSource)
        .filter(source -> source instanceof MethodSource)
        .isPresent();
      return parentIsMethodSource ? getFullTestPath(testIdentifier) : getName(testIdentifier);
    }

    static class LimitedStackTracePrintWriter extends PrintWriter {
      public static final String CAUSED_BY = "Caused by: ";
      private final int headLimit;
      private final int tailLimit;
      private final List<String> tailLines = new ArrayList<>(0);
      private boolean newLine = false;
      private boolean inCausedBy = false;
      private int headLength = 0;
      private int tailLength = 0;

      LimitedStackTracePrintWriter(StringWriter out, int limit) {
        super(out);
        // Leave 10% for final 'caused by'
        tailLimit = limit / 10;
        headLimit = limit - tailLimit;
      }

      @Override
      public void print(String x) {
        if (x == null) return;
        int headLeft = headLimit - headLength;
        if (headLeft > 0) {
          // write while within head limit
          if (x.length() >= headLeft) {
            x = x.substring(0, headLeft - 1);
          }
          super.print(x);
          headLength += x.length();
          newLine = true;
          return;
        }
        if (x.contains(CAUSED_BY)) {
          tailLines.clear();
          tailLength = 0;
          inCausedBy = true;
        }
        if (inCausedBy) {
          // add to last lines if they are within their tail limit
          int tailLeft = tailLimit - tailLength;
          if (tailLeft > 0) {
            if (x.length() >= tailLeft) {
              x = x.substring(0, tailLeft + 1);
            }
            tailLines.add(x);
            tailLength += x.length() + 1;
          }
        }
        else {
          // just skip, it's not 'caused by' section (yet?)
        }
      }

      @Override
      public void println() {
        if (newLine) {
          newLine = false;
          super.println();
          headLength++;
        }
      }

      private void finish() {
        if (!tailLines.isEmpty()) {
          super.print("...");
          super.println();
          for (String line : tailLines) {
            super.print(line);
            super.println();
          }
          tailLines.clear();
        }
        super.flush();
      }

      @Override
      public void close() {
        finish();
        super.close();
      }
    }
  }

  public static class CommonTestClassesFilter implements ClassNameFilter {
    private static final MethodHandle isClassNameIncluded;

    static {
      try {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        isClassNameIncluded = MethodHandles.publicLookup()
          .findStatic(Class.forName("com.intellij.TestCaseLoader", true, classLoader),
                      "isClassNameIncluded", MethodType.methodType(boolean.class, String.class));
        boolean ignored = (boolean)isClassNameIncluded.invokeExact(Object.class.getName() + "Test");  // force load test classes filter, *Test matches ClassFinder#isSuitableTestClassName
      }
      catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public FilterResult apply(String className) {
      try {
        if ((boolean)isClassNameIncluded.invokeExact(className)) {
          return FilterResult.included(null);
        }
        return FilterResult.excluded(null);
      }
      catch (Throwable e) {
        return FilterResult.excluded(e.getMessage());
      }
    }
  }

  public static class BucketingClassNameFilter implements ClassNameFilter {
    private static final MethodHandle matchesCurrentBucket;

    static {
      try {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        matchesCurrentBucket = MethodHandles.publicLookup()
          .findStatic(Class.forName("com.intellij.TestCaseLoader", true, classLoader),
                      "matchesCurrentBucket", MethodType.methodType(boolean.class, String.class));
        boolean ignored = (boolean)matchesCurrentBucket.invokeExact(Object.class.getName());  // force load bucketing scheme
      }
      catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public FilterResult apply(String className) {
      try {
        if ((boolean)matchesCurrentBucket.invokeExact(className)) {
          return FilterResult.included(null);
        }
        return FilterResult.excluded(null);
      }
      catch (Throwable e) {
        return FilterResult.excluded(e.getMessage());
      }
    }
  }

  public static class PerformancePostDiscoveryFilter implements PostDiscoveryFilter {
    private static final boolean isIncludingPerformanceTestsRun;
    private static final boolean isPerformanceTestsRun;
    private static final MethodHandle isPerformanceTest;

    static {
      try {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        isIncludingPerformanceTestsRun = (boolean)MethodHandles.publicLookup()
          .findStatic(Class.forName("com.intellij.TestCaseLoader", true, classLoader),
                      "isIncludingPerformanceTestsRun", MethodType.methodType(boolean.class))
          .invokeExact();
        isPerformanceTestsRun = (boolean)MethodHandles.publicLookup()
          .findStatic(Class.forName("com.intellij.TestCaseLoader", true, classLoader),
                      "isPerformanceTestsRun", MethodType.methodType(boolean.class))
          .invokeExact();
        isPerformanceTest = MethodHandles.publicLookup()
          .findStatic(Class.forName("com.intellij.testFramework.TestFrameworkUtil", true, classLoader),
                      "isPerformanceTest", MethodType.methodType(boolean.class, String.class, Class.class));
      }
      catch (Throwable e) {
        throw new RuntimeException(e);
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
        return isIncluded(methodSource.getJavaClass(), methodSource.getMethodName());
      }
      if (source instanceof ClassSource classSource) {
        return isIncluded(classSource.getJavaClass(), null);
      }
      return FilterResult.included("Unknown source type " + source.getClass());
    }

    private static FilterResult isIncluded(Class<?> aClass, String methodName) {
      try {
        if (isIncludingPerformanceTestsRun || isPerformanceTestsRun == (boolean)isPerformanceTest.invokeExact(methodName, aClass)) {
          return FilterResult.included(null);
        }
        return FilterResult.excluded(null);
      }
      catch (Throwable e) {
        return FilterResult.excluded(e.getMessage());
      }
    }
  }

  public static class IgnorePostDiscoveryFilter implements PostDiscoveryFilter {
    private static final Class<? extends Annotation> ignoreJUnit3;
    private static final Class<? extends Annotation> ignoreIJ;

    static {
      try {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //noinspection unchecked
        ignoreJUnit3 = (Class<? extends Annotation>)Class.forName("com.intellij.idea.IgnoreJUnit3", true, classLoader);
        //noinspection unchecked
        ignoreIJ = (Class<? extends Annotation>)Class.forName("com.intellij.idea.IJIgnore", true, classLoader);
      }
      catch (Throwable e) {
        throw new RuntimeException(e);
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
        Method method = methodSource.getJavaMethod();
        Class<?> aClass = methodSource.getJavaClass();
        if (method.isAnnotationPresent(ignoreJUnit3) || method.isAnnotationPresent(ignoreIJ) ||
            aClass.isAnnotationPresent(ignoreJUnit3) || aClass.isAnnotationPresent(ignoreIJ)) {
          return FilterResult.excluded("Ignored");
        }
        return FilterResult.included(null);
      }
      if (source instanceof ClassSource classSource) {
        Class<?> aClass = classSource.getJavaClass();
        if (aClass.isAnnotationPresent(ignoreJUnit3) || aClass.isAnnotationPresent(ignoreIJ)) {
          return FilterResult.excluded("Ignored");
        }
        return FilterResult.included(null);
      }
      return FilterResult.included("Unknown source type " + source.getClass());
    }
  }

  public static class HeadlessPostDiscoveryFilter implements PostDiscoveryFilter {
    private static final boolean shouldSkipHeadless;
    private static final Class<? extends Annotation> headless;

    static {
      try {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        shouldSkipHeadless = (boolean)MethodHandles.publicLookup()
          .findStatic(Class.forName("com.intellij.testFramework.TestFrameworkUtil", true, classLoader),
                      "shouldSkipHeadless", MethodType.methodType(boolean.class))
          .invokeExact();
        //noinspection unchecked
        headless = (Class<? extends Annotation>)Class.forName("com.intellij.testFramework.SkipInHeadlessEnvironment", true, classLoader);
      }
      catch (Throwable e) {
        throw new RuntimeException(e);
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
        Class<?> aClass = methodSource.getJavaClass();
        if (shouldSkipHeadless && aClass.isAnnotationPresent(headless)) {
          return FilterResult.excluded("Ignored");
        }
        return FilterResult.included(null);
      }
      if (source instanceof ClassSource classSource) {
        Class<?> aClass = classSource.getJavaClass();
        if (shouldSkipHeadless && aClass.isAnnotationPresent(headless)) {
          return FilterResult.excluded("Ignored");
        }
        return FilterResult.included(null);
      }
      return FilterResult.included("Unknown source type " + source.getClass());
    }
  }
}
