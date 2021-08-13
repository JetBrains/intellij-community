// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests;

import jetbrains.buildServer.messages.serviceMessages.MapSerializerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import junit.framework.TestSuite;
import org.junit.AssumptionViolatedException;
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
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class JUnit5SuiteRunner {
  public static void main(String[] args) throws ClassNotFoundException {
    Class<?> aClass = Class.forName(args[0], true, JUnit5SuiteRunner.class.getClassLoader());
    Launcher launcher = LauncherFactory.create();
    DiscoverySelector selector;
    if (args.length == 1) {
      selector = DiscoverySelectors.selectClass(aClass);
    }
    else {
      selector = DiscoverySelectors.selectMethod(aClass, args[1]);
    }
    launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selector).build(), new TCTestExecutionListener());
    System.exit(0);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static class TCTestExecutionListener implements TestExecutionListener {
    private final PrintStream myPrintStream;
    private TestPlan myTestPlan;
    private long myCurrentTestStart;
    private int myFinishCount = 0;
    
    private TCTestExecutionListener() {
      myPrintStream = System.out;
      myPrintStream.println("##teamcity[enteredTheMatrix]");
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
      TestExecutionResult.Status status = testExecutionResult.getStatus();
      final Throwable throwable = testExecutionResult.getThrowable().orElse(null);
      if (throwable instanceof AssumptionViolatedException) {
        status = TestExecutionResult.Status.ABORTED;
      }
      executionFinished(testIdentifier, status, throwable, null);
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
            if (className.equals(TestSuite.class.getName())) {
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
        attrs.put("details", getTrace(ex));
      }
      finally {
        myPrintStream.println(ServiceMessage.asString(messageName, attrs));
      }
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
