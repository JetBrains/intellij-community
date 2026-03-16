// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import jetbrains.buildServer.messages.serviceMessages.TestFinished;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import jetbrains.buildServer.messages.serviceMessages.TestStdOut;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted;
import junit.framework.JUnit4TestAdapter;
import junit.framework.JUnit4TestAdapterCache;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.junit.platform.commons.logging.LogRecordListener;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.vintage.engine.VintageTestEngine;
import org.junit.vintage.engine.descriptor.VintageTestDescriptor;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Runs JUnit 3/4 tests using {@linkplain VintageTestEngine}/{@linkplain OrderedVintageTestEngine}, or JUnit 5 tests using {@linkplain JupiterTestEngine}.
 *
 * Supported options:
 * - __class__ className[;...]
 * - __package__ packageName
 * - __classpathroot__
 *   if {@systemProperty intellij.build.test.engine.vintage} is set, {@code only} runs only JUnit 3/4 tests using {@linkplain OrderedVintageTestEngine}, {@code false} runs only JUnit 5 tests; otherwise, both are run
 *   if {@systemProperty intellij.build.test.reverse.order} is set, JUnit 3/4 tests are run in reverse order
 * - className
 * - className methodName
 * if {@systemProperty intellij.build.test.list.classes} is set, only test discovery is performed and the resulting list of test classes is saved to the file specified by this property
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class JUnit5TeamCityRunner {
  private static final String ENGINE_VINTAGE = System.getProperty("intellij.build.test.engine.vintage");
  private static final String REVERSE_ORDER = System.getProperty("intellij.build.test.reverse.order");

  public static void main(String[] args) throws ClassNotFoundException {
    if (args.length != 1 && args.length != 2) {
      System.err.printf("Expected one or two arguments, got %d: %s%n", args.length, Arrays.toString(args));
      System.exit(1);
    }

    TCExecutionListener listener = null;
    Throwable caughtException = null;

    try {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      List<? extends DiscoverySelector> selectors;
      List<Filter<?>> filters = new ArrayList<>(0);
      Optional<OrderedVintageTestEngine> optionalOrderedVintageEngine = Optional.empty();

      if (args[0].equals("__class__")) {
        String[] classNames = args[1].split(";");
        selectors = Arrays.stream(classNames).map(DiscoverySelectors::selectClass).toList();
        // no filters
      }
      else if (args[0].equals("__package__")) {
        String packageName = args[1];
        selectors = Collections.singletonList(DiscoverySelectors.selectPackage(packageName));
        // exclude subpackages
        filters.add(ClassNameFilter.excludeClassNamePatterns("\\Q" + args[1] + "\\E\\.[^.]+\\..*"));
      }
      else if (args[0].equals("__classpathroot__")) {
        Set<Path> classpathRoots = JUnit5TeamCityRunnerForTestsOnClasspath.getClassPathRoots(classLoader);
        if (classpathRoots == null) throw new RuntimeException("Failed to get classpath roots");
        selectors = DiscoverySelectors.selectClasspathRoots(classpathRoots);
        filters.add(JUnit5TeamCityRunnerForTestsOnClasspath.createClassNameFilter(classLoader));      // name check
        filters.add(JUnit5TeamCityRunnerForTestsOnClasspath.createPostDiscoveryFilter(classLoader));  // bucketing
        if (!"false".equals(ENGINE_VINTAGE)) filters.add(new IgnorePostDiscoveryFilter());            // IJIgnore and Ignore support in JUnit 3/4
        filters.add(new PerformancePostDiscoveryFilter());                                            // PerformanceUnitTest support
        if (!"false".equals(ENGINE_VINTAGE)) filters.add(new HeadlessPostDiscoveryFilter());          // SkipInHeadlessEnvironment support in JUnit 3/4

        // filter engines
        if ("false".equals(ENGINE_VINTAGE)) {  // JUnit 5 tests only
          filters.add(EngineFilter.excludeEngines(VintageTestDescriptor.ENGINE_ID));
        }
        else {
          // TODO: use vintage by default
          optionalOrderedVintageEngine = Optional.of(new OrderedVintageTestEngine());
          filters.add(EngineFilter.excludeEngines(VintageTestDescriptor.ENGINE_ID));  // mask VintageTestEngine to avoid running JUnit 3/4 tests twice

          if ("only".equals(ENGINE_VINTAGE)) {  // JUnit 3/4 tests only
            filters.add(EngineFilter.includeEngines(OrderedVintageTestEngine.ENGINE_ID));
          }
          else if (ENGINE_VINTAGE == null) {  // all tests
          }
          else throw new RuntimeException("Unsupported 'intellij.build.test.engine.vintage' value: " + ENGINE_VINTAGE);
        }
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

      Launcher launcher = LauncherFactory.create(LauncherConfig.builder().addTestEngines(optionalOrderedVintageEngine.stream().toArray(TestEngine[]::new)).build());
      LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectors)
        .filters(filters.toArray(Filter[]::new))
        .build();
      TestPlan testPlan = launcher.discover(discoveryRequest);

      boolean reportAsBootstrapTestsSuite = "only".equals(ENGINE_VINTAGE);  // mask suite names to preserve test identity on TeamCity
      listener = new TCExecutionListener(reportAsBootstrapTestsSuite);

      if (JUnit5TeamCityRunnerForTestsOnClasspath.LIST_CLASSES != null) {
        JUnit5TeamCityRunnerForTestsOnClasspath.saveListOfTestClasses(testPlan);  // save only
      }
      else {
        launcher.execute(testPlan, listener);
      }
    }
    catch (Throwable e) {
      caughtException = e;
    }
    finally {
      JUnit5TeamCityRunnerForTestsOnClasspath.assertNoUnhandledExceptions("JUnit5TeamCityRunnerForTestAllSuite", caughtException);  // TODO: rename to JUnit5TeamCityRunner
    }

    // Determine exit code OUTSIDE of try/catch/finally to avoid finally overriding the exit code
    int exitCode;
    if (caughtException != null) {
      exitCode = 1;
    }
    else if (!listener.smthExecuted()) {
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

  public static JUnit4TestAdapterCache createJUnit4TestAdapterCache() {
    return new JUnit4TestAdapterCache() {
      @Override
      public RunNotifier getNotifier(final TestResult result, final JUnit4TestAdapter adapter) {
        RunNotifier notifier = new RunNotifier();
        notifier.addListener(new RunListener() {
          @Override
          public void testFailure(Failure failure) {
            result.addError(asTest(failure.getDescription()), failure.getException());
          }

          @Override
          public void testFinished(Description description) {
            result.endTest(asTest(description));
          }

          @Override
          public void testStarted(Description description) {
            result.startTest(asTest(description));
          }

          @Override
          public void testIgnored(Description description) {
            result.addError(asTest(description), IgnoreException.INSTANCE);
          }

          @Override
          public void testAssumptionFailure(Failure failure) {
            testFailure(failure);
          }
        });
        return notifier;
      }
    };
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
        System.out.println(ServiceMessage.asString(ServiceMessageTypes.BUILD_PROBLEM, Map.of("description", r.getMessage())));
      }
    }
  }

  public static class TCExecutionListener implements TestExecutionListener {
    /**
     * The same constant as com.intellij.rt.execution.TestListenerProtocol.CLASS_CONFIGURATION
     */
    private static final String CLASS_CONFIGURATION = "Class Configuration";
    private final PrintStream myPrintStream;

    private static final String BOOTSTRAP_TESTS_SUITE_NAME = "com.intellij.tests.BootstrapTests";
    private final boolean myReportAsBootstrapTestsSuite;

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

    public TCExecutionListener() {
      this(false);
    }

    private TCExecutionListener(boolean reportAsBootstrapTestsSuite) {
      myReportAsBootstrapTestsSuite = reportAsBootstrapTestsSuite;
      myPrintStream = System.out;
      myPrintStream.println("##teamcity[enteredTheMatrix]");
    }

    public boolean smthExecuted() {
      return myCurrentTestStart != 0;
    }

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
      if (myReportAsBootstrapTestsSuite) {
        myPrintStream.println(new TestSuiteStarted(BOOTSTRAP_TESTS_SUITE_NAME));
      }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
      if (myReportAsBootstrapTestsSuite) {
        myPrintStream.println(new TestSuiteFinished(BOOTSTRAP_TESTS_SUITE_NAME));
      }
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
        if (!myReportAsBootstrapTestsSuite) {
          myPrintStream.println(new TestSuiteStarted(getName(testIdentifier)));
        }
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

        TestLocationStorage.recordTestLocation(testIdentifier, status, getFullTestPath(testIdentifier));

        testFinished(testIdentifier, duration);
        myFinishCount++;
      }
      else if (hasNonTrivialParent(testIdentifier)) {
        String messageName = null;
        if (status == TestExecutionResult.Status.FAILED) {
          messageName = ServiceMessageTypes.TEST_FAILED;
        }
        else if (status == TestExecutionResult.Status.ABORTED) {
          messageName = ServiceMessageTypes.TEST_IGNORED;
        }
        if (messageName != null) {
          if (status == TestExecutionResult.Status.FAILED) {
            myPrintStream.println(new TestStarted(CLASS_CONFIGURATION, false, null));
            testFailure(CLASS_CONFIGURATION, messageName, throwableOptional, 0, reason);
            myPrintStream.println(new TestFinished(CLASS_CONFIGURATION, 0));
          }

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
        if (!myReportAsBootstrapTestsSuite) {
          myPrintStream.println(new TestSuiteFinished(getName(testIdentifier)));
        }
        if (status == TestExecutionResult.Status.ABORTED) myCurrentTestStart = 1;  // mark ignored classes as #smthExecuted
      }
    }

    private static boolean hasNonTrivialParent(TestIdentifier testIdentifier) {
      return testIdentifier.getParentId().isPresent();
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
          return s instanceof MethodSource ? ((MethodSource)s).getClassName() + "." + displayName : null;
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
        if (reason != null) {
          attrs.put("message", limit(reason));
        }
        if (ex != null) {
          attrs.put("details", getTrace(ex, MAX_STACKTRACE_MESSAGE_LENGTH));
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

  public static final class OrderedVintageTestEngine implements TestEngine {
    public static final String ENGINE_ID = "ordered-vintage";

    @SuppressWarnings("FieldMayBeStatic") private final VintageTestEngine delegate = new VintageTestEngine();

    @Override
    public String getId() {
      return ENGINE_ID;
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
      TestDescriptor root = delegate.discover(request, uniqueId);
      root.orderChildren(children -> {
        Collections.sort(children, (a, b) -> {
          assert a.getSource().isPresent() && a.getSource().get() instanceof ClassSource;
          ClassSource aClass = (ClassSource)a.getSource().get();
          assert b.getSource().isPresent() && b.getSource().get() instanceof ClassSource;
          ClassSource bClass = (ClassSource)b.getSource().get();
          return aClass.getJavaClass().getName().compareTo(bClass.getJavaClass().getName());
        });
        if ("true".equals(REVERSE_ORDER)) Collections.reverse(children);
        return children;
      });
      return root;
    }

    @Override
    public void execute(ExecutionRequest request) {
      delegate.execute(request);
    }
  }
}
