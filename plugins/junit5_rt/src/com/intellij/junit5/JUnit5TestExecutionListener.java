/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.junit5;

import com.intellij.junit4.ExpectedPatterns;
import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.JavaClassSource;
import org.junit.platform.engine.support.descriptor.JavaMethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;
import org.opentest4j.ValueWrapper;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JUnit5TestExecutionListener implements TestExecutionListener {
  private final PrintStream myPrintStream;
  private TestPlan myTestPlan;
  private long myCurrentTestStart;
  private int myFinishCount = 0;
  private String myRootName;
  private Set<TestIdentifier> myRoots;

  public JUnit5TestExecutionListener() {
    this(System.out);
  }

  public JUnit5TestExecutionListener(PrintStream printStream) {
    myPrintStream = printStream;
    myPrintStream.println("##teamcity[enteredTheMatrix]");
  }

  @Override
  public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
    StringBuilder builder = new StringBuilder();
    builder.append("timestamp = ").append(entry.getTimestamp());
    entry.getKeyValuePairs().forEach((key, value) -> builder.append(", ").append(key).append(" = ").append(value));
    myPrintStream.println(builder.toString());
  }

  @Override
  public void testPlanExecutionStarted(TestPlan testPlan) {
    if (myRootName != null) {
      int lastPointIdx = myRootName.lastIndexOf('.');
      String name = myRootName;
      String comment = null;
      if (lastPointIdx >= 0) {
        name = myRootName.substring(lastPointIdx + 1);
        comment = myRootName.substring(0, lastPointIdx);
      }

      myPrintStream.println("##teamcity[rootName name = \'" + escapeName(name) +
                            (comment != null ? ("\' comment = \'" + escapeName(comment)) : "") + "\'" +
                            " location = \'java:suite://" + escapeName(myRootName) +
                            "\']");
    }
  }

  @Override
  public void testPlanExecutionFinished(TestPlan testPlan) {
    myTestPlan = null;
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
    else if (!myRoots.contains(testIdentifier)){
      myFinishCount = 0;
      myPrintStream.println("##teamcity[testSuiteStarted" + idAndName(testIdentifier) + "\']");
    }
  }

  private static String idAndName(TestIdentifier testIdentifier) {
    return idAndName(testIdentifier, testIdentifier.getDisplayName());
  }

  private static String idAndName(TestIdentifier testIdentifier, String displayName) {
    return " id=\'" + testIdentifier.getUniqueId().toString() + "\' name=\'" + escapeName(displayName);
  }

  @Override
  public void dynamicTestRegistered(TestIdentifier testIdentifier) {
    int i = 0;
  }

  @Override
  public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
    final TestExecutionResult.Status status = testExecutionResult.getStatus();
    final Throwable throwableOptional = testExecutionResult.getThrowable().orElse(null);
    executionFinished(testIdentifier, status, throwableOptional, null);
  }

  private void executionFinished(TestIdentifier testIdentifier,
                                 TestExecutionResult.Status status,
                                 Throwable throwableOptional,
                                 String reason) {
    final String displayName = testIdentifier.getDisplayName();
    if (testIdentifier.isTest()) {
      final long duration = getDuration();
      if (status == TestExecutionResult.Status.FAILED) {
        testFailure(testIdentifier, MapSerializerUtil.TEST_FAILED, throwableOptional, duration, reason, true);
      }
      else if (status == TestExecutionResult.Status.ABORTED) {
        testFailure(testIdentifier, MapSerializerUtil.TEST_IGNORED, throwableOptional, duration, reason, true);
      }
      testFinished(testIdentifier, duration);
      myFinishCount++;
    }
    else if (!myRoots.contains(testIdentifier)){
      String messageName = null;
      if (status == TestExecutionResult.Status.FAILED) {
        messageName = MapSerializerUtil.TEST_FAILED;
      }
      else if (status == TestExecutionResult.Status.ABORTED) {
        messageName = MapSerializerUtil.TEST_IGNORED;
      }
      if (messageName != null && myFinishCount == 0) {
        final Set<TestIdentifier> descendants = myTestPlan.getDescendants(testIdentifier);
        if (!descendants.isEmpty()) {
          for (TestIdentifier childIdentifier : descendants) {
            testStarted(childIdentifier);
            testFailure(childIdentifier, messageName, throwableOptional, 0, reason, true);
            testFinished(childIdentifier, 0);
          }
        }
        else {
          testStarted(testIdentifier);
          testFailure(testIdentifier, messageName, throwableOptional, 0, reason, true);
          testFinished(testIdentifier, 0);
          myFinishCount++;
        }
      }
      myPrintStream.println("##teamcity[testSuiteFinished " + idAndName(testIdentifier, displayName) + "\']");
    }
  }

  protected long getDuration() {
    return System.currentTimeMillis() - myCurrentTestStart;
  }

  private void testStarted(TestIdentifier testIdentifier) {
    myPrintStream.println("\n##teamcity[testStarted" + idAndName(testIdentifier) + "\']");
  }
  
  private void testFinished(TestIdentifier testIdentifier, long duration) {
    myPrintStream.println("\n##teamcity[testFinished" + idAndName(testIdentifier) + (duration > 0 ? "\' duration=\'" + Long.toString(duration) : "") + "\']");
  }
  
  private void testFailure(TestIdentifier testIdentifier,
                           String messageName, 
                           Throwable ex,
                           long duration, 
                           String reason,
                           boolean includeThrowable) {
    final Map<String, String> attrs = new HashMap<>();
    attrs.put("name", testIdentifier.getDisplayName());
    attrs.put("id", testIdentifier.getUniqueId().toString());
    if (duration > 0) {
      attrs.put("duration", Long.toString(duration));
    }
    if (reason != null) {
      attrs.put("message", reason);
    }
    try {
      if (ex != null) {
        ComparisonFailureData failureData = null;
        if (ex instanceof MultipleFailuresError && ((MultipleFailuresError)ex).hasFailures()) {
          for (AssertionError assertionError : ((MultipleFailuresError)ex).getFailures()) {
            testFailure(testIdentifier, messageName, assertionError, duration, reason, false);
          }
        }
        else if (ex instanceof AssertionFailedError && ((AssertionFailedError)ex).isActualDefined() && ((AssertionFailedError)ex).isExpectedDefined()) {
          final ValueWrapper actual = ((AssertionFailedError)ex).getActual();
          final ValueWrapper expected = ((AssertionFailedError)ex).getExpected();
          failureData = new ComparisonFailureData(expected.getStringRepresentation(), actual.getStringRepresentation());
        }
        else {
          //try to detect failure with junit 4 if present in the classpath
          try {
            failureData = ExpectedPatterns.createExceptionNotification(ex);
          }
          catch (Throwable ignore) {}
        }

        if (includeThrowable || failureData == null) {
          ComparisonFailureData.registerSMAttributes(failureData, getTrace(ex), ex.getMessage(), attrs, ex);
        }
        else {
          ComparisonFailureData.registerSMAttributes(failureData, "", "", attrs, ex, "");
        }
      }
    }
    finally {
      myPrintStream.println("\n" + MapSerializerUtil.asString(messageName, attrs));
    }
  }

  protected String getTrace(Throwable ex) {
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(stringWriter);
    ex.printStackTrace(writer);
    return stringWriter.toString();
  }


  public void sendTree(TestPlan testPlan, String rootName) {
    myTestPlan = testPlan;
    myRootName = rootName;
    myRoots = testPlan.getRoots();
    for (TestIdentifier root : myRoots) {
      assert root.isContainer();
      for (TestIdentifier testIdentifier : testPlan.getChildren(root)) {
        sendTreeUnderRoot(testPlan, testIdentifier);
      }
    }
    myPrintStream.println("##teamcity[treeEnded]");
  }

  private void sendTreeUnderRoot(TestPlan testPlan, TestIdentifier root) {
    final String idAndName = idAndName(root);
    if (root.isContainer()) {
      myPrintStream.println("##teamcity[suiteTreeStarted" + idAndName + "\' locationHint=\'java:suite://" + escapeName(getClassName(root)) + "\']");
      for (TestIdentifier childIdentifier : testPlan.getChildren(root)) {
        sendTreeUnderRoot(testPlan, childIdentifier);
      }
      myPrintStream.println("##teamcity[suiteTreeEnded" + idAndName + "\']");
    }
    else if (root.isTest()) {
      myPrintStream.println("##teamcity[suiteTreeNode " + idAndName + "\' locationHint=\'java:test://" + escapeName(getClassName(root) + "." + getMethodName(root)) + "\']");
    }
  }


  private static String escapeName(String str) {
    return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
  }

  static String getClassName(TestIdentifier description) {
    return description.getSource().map(source -> {
      if (source instanceof JavaMethodSource) {
        return ((JavaMethodSource)source).getJavaClass().getName();
      }
      if (source instanceof JavaClassSource) {
        return ((JavaClassSource)source).getJavaClass().getName();
      }
      return null;
    }).orElse(null);
  }

  static String getMethodName(TestIdentifier testIdentifier) {
    return testIdentifier.getSource().map((source) -> {
      if (source instanceof JavaMethodSource) {
        return ((JavaMethodSource)source).getJavaMethodName();
      }
      return null;
    }).orElse(null);
  }
}
