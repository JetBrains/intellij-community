// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import jetbrains.buildServer.messages.serviceMessages.MapSerializerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import junit.framework.JUnit4TestAdapter;
import junit.framework.JUnit4TestAdapterCache;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.junit.platform.engine.*;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

// Used to run JUnit 3/4 tests via JUnit 5 runtime
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class JUnit5TeamCityRunnerForTestAllSuite {
  public static void main(String[] args) throws ClassNotFoundException {
    if (args.length != 1 && args.length != 2) {
      System.err.printf("Expected one or two arguments, got %d: %s%n", args.length, Arrays.toString(args));
      System.exit(1);
    }
    try {
      Launcher launcher = LauncherFactory.create(LauncherConfig.builder().enableLauncherSessionListenerAutoRegistration(false).build());
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      List<? extends DiscoverySelector> selectors;
      List<Filter<?>> filters = new ArrayList<>(0);
      if (args.length == 1) {
        selectors = Collections.singletonList(DiscoverySelectors.selectClass(args[0]));
      }
      else if (args[0].equals("__package__")) {
        selectors = Collections.singletonList(DiscoverySelectors.selectPackage(args[1]));
        // exclude subpackages
        filters.add(ClassNameFilter.excludeClassNamePatterns("\\Q" + args[1] + "\\E\\.[^.]+\\..*"));
      }
      else if (args[0].equals("__classes__")) {
        String[] classes = args[1].split(";");
        selectors = Arrays.stream(classes).map(DiscoverySelectors::selectClass).toList();
      }
      else {
        selectors = Collections.singletonList(DiscoverySelectors.selectMethod(args[0], args[1]));
      }
      if (Boolean.getBoolean("idea.performance.tests.discovery.filter")) {
        // Add filter
        filters.add(createPerformancePostDiscoveryFilter(classLoader));
      }
      LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectors)
        .filters(filters.toArray(new Filter[0]))
        .build();
      TCExecutionListener listener = new TCExecutionListener();
      TestPlan testPlan = launcher.discover(discoveryRequest);
      launcher.execute(testPlan, listener);
      if (!listener.smthExecuted()) {
        //see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR
        System.exit(42);
      }
    }
    catch (Throwable x) {
      //noinspection CallToPrintStackTrace
      x.printStackTrace();
    }
    finally {
      System.exit(0);
    }
  }

  private static PostDiscoveryFilter createPerformancePostDiscoveryFilter(ClassLoader classLoader)
    throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
    final MethodHandle method = MethodHandles.publicLookup()
      .findStatic(Class.forName("com.intellij.testFramework.TestFrameworkUtil", true, classLoader),
                  "isPerformanceTest", MethodType.methodType(boolean.class, String.class, String.class));
    return new PostDiscoveryFilter() {
      private FilterResult isIncluded(String className, String methodName) {
        try {
          if ((boolean)method.invokeExact(methodName, className)) {
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
          return isIncluded(methodSource.getClassName(), methodSource.getMethodName());
        }
        if (source instanceof ClassSource classSource) {
          return isIncluded(classSource.getClassName(), null);
        }
        return FilterResult.included("Unknown source type " + source.getClass());
      }
    };
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

  public static class TCExecutionListener implements TestExecutionListener {
    /**
     * The same constant as com.intellij.rt.execution.TestListenerProtocol.CLASS_CONFIGURATION
     */
    private static final String CLASS_CONFIGURATION = "Class Configuration";
    private final PrintStream myPrintStream;
    private TestPlan myTestPlan;
    private long myCurrentTestStart = 0;
    private int myFinishCount = 0;
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
      myPrintStream = System.out;
      myPrintStream.println("##teamcity[enteredTheMatrix]");
    }

    public boolean smthExecuted() {
      return myCurrentTestStart != 0;
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
      StringBuilder builder = new StringBuilder();
      builder.append("timestamp = ").append(entry.getTimestamp());
      entry.getKeyValuePairs().forEach((key, value) -> builder.append(", ").append(key).append(" = ").append(value));
      builder.append("\n");
      myPrintStream.println("##teamcity[testStdOut" + idAndName(testIdentifier) + " out = '" + escapeName(builder.toString()) + "']");
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
        myPrintStream.println("##teamcity[testSuiteStarted" + idAndName(testIdentifier) + "]");
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
      if (testIdentifier.isTest()) {
        final long duration = getDuration();
        if (status == TestExecutionResult.Status.FAILED) {
          testFailure(testIdentifier, ServiceMessageTypes.TEST_FAILED, throwableOptional, duration, reason);
        }
        else if (status == TestExecutionResult.Status.ABORTED) {
          testFailure(testIdentifier, ServiceMessageTypes.TEST_IGNORED, throwableOptional, duration, reason);
        }
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
            String parentId = getParentId(testIdentifier);
            String nameAndId = " name='" + CLASS_CONFIGURATION +
                               "' nodeId='" + escapeName(getId(testIdentifier)) +
                               "' parentNodeId='" + escapeName(parentId) + "'";
            myPrintStream.println("##teamcity[testStarted" + nameAndId + "]");
            testFailure(CLASS_CONFIGURATION, getId(testIdentifier), parentId, messageName, throwableOptional, 0, reason);
            myPrintStream.println("##teamcity[testFinished" + nameAndId + "]");
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
        myPrintStream.println("##teamcity[testSuiteFinished" + idAndName(testIdentifier) + "]");
      }
    }

    private static boolean hasNonTrivialParent(TestIdentifier testIdentifier) {
      return testIdentifier.getParentId().isPresent();
    }

    protected long getDuration() {
      return (System.nanoTime() - myCurrentTestStart) / 1_000_000;
    }

    private void testStarted(TestIdentifier testIdentifier) {
      myPrintStream.println("##teamcity[testStarted" + idAndName(testIdentifier) + " captureStandardOutput='" + CAPTURE_STANDARD_OUTPUT + "']");
    }

    private void testFinished(TestIdentifier testIdentifier, long duration) {
      myPrintStream.println(
        "##teamcity[testFinished" + idAndName(testIdentifier) + (duration > 0 ? " duration='" + duration + "'" : "") + "]");
    }

    private void testFailure(TestIdentifier testIdentifier,
                             String messageName,
                             Throwable ex,
                             long duration,
                             String reason) {
      testFailure(getName(testIdentifier), getId(testIdentifier), getParentId(testIdentifier), messageName, ex, duration, reason);
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
                             String id,
                             String parentId,
                             String messageName,
                             Throwable ex,
                             long duration,
                             String reason) {
      final Map<String, String> attrs = new LinkedHashMap<>();
      try {
        attrs.put("name", methodName);
        attrs.put("id", id);
        attrs.put("nodeId", id);
        attrs.put("parentNodeId", parentId);
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
              testFailure(methodName, id, parentId, messageName, assertionError, duration, reason);
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

    protected static String getTrace(Throwable ex, int limit) {
      final StringWriter stringWriter = new StringWriter();
      final LimitedStackTracePrintWriter writer = new LimitedStackTracePrintWriter(stringWriter, limit);
      ex.printStackTrace(writer);
      writer.close();
      return stringWriter.toString();
    }

    private static String getId(TestIdentifier identifier) {
      return identifier.getUniqueId();
    }

    private String idAndName(TestIdentifier testIdentifier) {
      String id = getId(testIdentifier);
      String name = getName(testIdentifier);
      String parentId = getParentId(testIdentifier);
      return " id='" + escapeName(id) +
             "' name='" + escapeName(name) +
             "' nodeId='" + escapeName(id) +
             "' parentNodeId='" + escapeName(parentId) + "'";
    }

    private String getParentId(TestIdentifier testIdentifier) {
      Optional<TestIdentifier> parent = myTestPlan.getParent(testIdentifier);

      return parent
        .map(identifier -> identifier.getUniqueId())
        .orElse("0");
    }

    private static String escapeName(String str) {
      return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER2);
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
}
