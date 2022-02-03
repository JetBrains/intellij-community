// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import jetbrains.buildServer.messages.serviceMessages.MapSerializerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import junit.framework.JUnit4TestAdapter;
import junit.framework.JUnit4TestAdapterCache;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
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
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class JUnit5Runner {
  public static void main(String[] args) throws ClassNotFoundException {
    try {
      Class<?> aClass = Class.forName(args[0], true, JUnit5Runner.class.getClassLoader());
      Launcher launcher = LauncherFactory.create(LauncherConfig.builder().enableLauncherSessionListenerAutoRegistration(false).build());
      DiscoverySelector selector;
      if (args.length == 1) {
        selector = DiscoverySelectors.selectClass(aClass);
      }
      else {
        selector = DiscoverySelectors.selectMethod(aClass, args[1]);
      }
      TCExecutionListener listener = new TCExecutionListener();
      launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selector).build(), listener);
      if (!listener.smthExecuted()) {
        //see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR
        System.exit(42);
      }
    }
    finally {
      System.exit(0);
    }
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

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static class TCExecutionListener implements TestExecutionListener {
    private final PrintStream myPrintStream;
    private TestPlan myTestPlan;
    private long myCurrentTestStart = 0;
    private int myFinishCount = 0;
    
    TCExecutionListener() {
      myPrintStream = System.out;
      myPrintStream.println("##teamcity[enteredTheMatrix]");
    }
    
    boolean smthExecuted() {
      return myCurrentTestStart > 0;
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
    public void testPlanExecutionFinished(TestPlan testPlan) {
    }
  
    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
      executionStarted (testIdentifier);
      executionFinished(testIdentifier, TestExecutionResult.Status.ABORTED, null, reason);
    }
  
    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
      if (testIdentifier.isTest()) {
        testStarted(testIdentifier);
        myCurrentTestStart = System.currentTimeMillis();
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
      final String displayName = getName(testIdentifier);
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
      else if (hasNonTrivialParent(testIdentifier)){
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
            String nameAndId = " name='CLASS_CONFIGURATION' nodeId='" + escapeName(getId(testIdentifier)) +
                               "' parentNodeId='" + escapeName(parentId) + "' ";
            testFailure("CLASS_CONFIGURATION", getId(testIdentifier), parentId, messageName, throwableOptional, 0, reason);
            myPrintStream.println("##teamcity[testFinished" + nameAndId + "]");
          }
  
          final Set<TestIdentifier> descendants = myTestPlan != null ? myTestPlan.getDescendants(testIdentifier) : Collections.emptySet();
          if (!descendants.isEmpty() && myFinishCount == 0) {
            for (TestIdentifier childIdentifier : descendants) {
              testStarted(childIdentifier);
              testFailure(childIdentifier, ServiceMessageTypes.TEST_IGNORED, status == TestExecutionResult.Status.ABORTED ? throwableOptional : null, 0, reason);
              testFinished(childIdentifier, 0);
            }
            myFinishCount = 0;
          }
        }
        myPrintStream.println("##teamcity[testSuiteFinished " + idAndName(testIdentifier, displayName) + "]");
      }
    }
  
    private static boolean hasNonTrivialParent(TestIdentifier testIdentifier) {
      return testIdentifier.getParentId().isPresent();
    }
  
    protected long getDuration() {
      return System.currentTimeMillis() - myCurrentTestStart;
    }
  
    private void testStarted(TestIdentifier testIdentifier) {
      myPrintStream.println("##teamcity[testStarted" + idAndName(testIdentifier) + "]");
    }
    
    private void testFinished(TestIdentifier testIdentifier, long duration) {
      myPrintStream.println("##teamcity[testFinished" + idAndName(testIdentifier) + (duration > 0 ? " duration='" + duration + "'" : "") + "]");
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
          attrs.put("message", reason);
        }
        if (ex != null) {
          attrs.put("details", getTrace(ex));
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
            attrs.put("expected", ((AssertionFailedError)ex).getExpected().getStringRepresentation());
            attrs.put("actual", ((AssertionFailedError)ex).getActual().getStringRepresentation());
          }
          else {
            Class<? extends Throwable> aClass = ex.getClass();
            if (isComparisonFailure(aClass)){
              try {
                String expected = (String)aClass.getDeclaredMethod("getExpected").invoke(ex);
                String actual = (String)aClass.getDeclaredMethod("getActual").invoke(ex);

                attrs.put("expected", expected);
                attrs.put("actual", actual);
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
  
    protected String getTrace(Throwable ex) {
      final StringWriter stringWriter = new StringWriter();
      final PrintWriter writer = new PrintWriter(stringWriter);
      ex.printStackTrace(writer);
      return stringWriter.toString();
    }
  
    private static String getId(TestIdentifier identifier) {
      return identifier.getUniqueId();
    }
  
    private String idAndName(TestIdentifier testIdentifier) {
      return idAndName(testIdentifier, getName(testIdentifier));
    }
  
    private String idAndName(TestIdentifier testIdentifier, String displayName) {
      return " id='" + escapeName(getId(testIdentifier)) +
             "' name='" + escapeName(displayName) +
             "' nodeId='" + escapeName(getId(testIdentifier)) +
             "' parentNodeId='" + escapeName(getParentId(testIdentifier)) + "'";
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
    
  }
}
