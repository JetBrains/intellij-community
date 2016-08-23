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
import org.junit.platform.engine.support.descriptor.JavaMethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.opentest4j.AssertionFailedError;
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
      final long duration = System.currentTimeMillis() - myCurrentTestStart;
      if (status == TestExecutionResult.Status.FAILED) {
        testFailure(testIdentifier, MapSerializerUtil.TEST_FAILED, throwableOptional, duration, reason);
      }
      else if (status == TestExecutionResult.Status.ABORTED) {
        testFailure(testIdentifier, MapSerializerUtil.TEST_IGNORED, throwableOptional, duration, reason);
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
        for (TestIdentifier childIdentifier : myTestPlan.getDescendants(testIdentifier)) {
          testStarted(childIdentifier);
          testFailure(childIdentifier, messageName, throwableOptional, 0, reason);
          testFinished(childIdentifier, 0);
        }
      }
      myPrintStream.println("##teamcity[testSuiteFinished " + idAndName(testIdentifier, displayName) + "\']");
    }
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
                           String reason) {
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
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter writer = new PrintWriter(stringWriter);
        ex.printStackTrace(writer);
        ComparisonFailureData failureData = null;
        if (ex instanceof AssertionFailedError && ((AssertionFailedError)ex).isActualDefined() && ((AssertionFailedError)ex).isExpectedDefined()) {
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
        ComparisonFailureData.registerSMAttributes(failureData, stringWriter.toString(), ex.getMessage(), attrs, ex);
      }
    }
    finally {
      myPrintStream.println("\n" + MapSerializerUtil.asString(messageName, attrs));
    }
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
    Optional<JavaMethodSource> javaSource = getJavaSource(description);
    return javaSource.map(source -> source.getJavaClass().getName()).orElse(null);
  }

  static String getMethodName(TestIdentifier testIdentifier) {
    return getJavaSource(testIdentifier).map(JavaMethodSource::getJavaMethodName).orElse(null);
  }

  private static Optional<JavaMethodSource> getJavaSource(TestIdentifier testIdentifier) {
    return testIdentifier.getSource().filter(JavaMethodSource.class::isInstance).map(JavaMethodSource.class::cast);
  }
  
}
