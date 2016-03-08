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

import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.gen5.engine.TestExecutionResult;
import org.junit.gen5.engine.support.descriptor.JavaSource;
import org.junit.gen5.launcher.TestExecutionListener;
import org.junit.gen5.launcher.TestIdentifier;
import org.junit.gen5.launcher.TestPlan;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class JUnit5TestExecutionListener implements TestExecutionListener {
  private final PrintStream myPrintStream;
  private TestPlan myTestPlan;
  private long myCurrentTestStart;
  private int myFinishCount = 0;
  private String myRootName;

  public JUnit5TestExecutionListener() {
    this(System.out);
  }

  public JUnit5TestExecutionListener(PrintStream printStream) {
    myPrintStream = printStream;
    myPrintStream.println("##teamcity[enteredTheMatrix]");
  }

  @Override
  public void testPlanExecutionStarted(TestPlan testPlan) {
    myTestPlan = testPlan;
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
    testIgnored(testIdentifier, reason);
  }

  private void testIgnored(TestIdentifier testIdentifier, String reason) {
    if (testIdentifier.isTest()) {
      executionStarted(testIdentifier);
      Map<String, String> attrs = new HashMap<>();
      if (reason != null) {
        attrs.put("message", reason);
      }
      attrs.put("name", testIdentifier.getDisplayName());
      attrs.put("id", testIdentifier.getUniqueId().toString());
      myPrintStream.println(MapSerializerUtil.asString(MapSerializerUtil.TEST_IGNORED, attrs));
      
      testFinished(testIdentifier, System.currentTimeMillis() - myCurrentTestStart);
    }
    else {
      myTestPlan.getDescendants(testIdentifier).forEach(identifier -> testIgnored(identifier, reason));
    }
  }

  @Override
  public void executionStarted(TestIdentifier testIdentifier) {
    if (testIdentifier.isTest()) {
      testStarted(testIdentifier);
      myCurrentTestStart = System.currentTimeMillis();
    }
    else {
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
    final String displayName = testIdentifier.getDisplayName();
    final TestExecutionResult.Status status = testExecutionResult.getStatus();
    final Optional<Throwable> throwableOptional = testExecutionResult.getThrowable();
    if (testIdentifier.isTest()) {
      final long duration = System.currentTimeMillis() - myCurrentTestStart;
      if (status == TestExecutionResult.Status.FAILED) {
        testFailure(throwableOptional, MapSerializerUtil.TEST_FAILED, testIdentifier, duration);
      }
      else if (status == TestExecutionResult.Status.ABORTED) {
        testFailure(throwableOptional, MapSerializerUtil.TEST_IGNORED, testIdentifier, duration);
      }
      testFinished(testIdentifier, duration);
      myFinishCount++;
    }
    else {
      String messageName = null;
      if (status == TestExecutionResult.Status.FAILED) {
        messageName = MapSerializerUtil.TEST_FAILED;
      }
      else if (status == TestExecutionResult.Status.ABORTED) {
        messageName = MapSerializerUtil.TEST_IGNORED;
      }
      if (messageName != null && myFinishCount == 0) {
        for (TestIdentifier childIdentifier : myTestPlan.getChildren(testIdentifier)) {
          testStarted(childIdentifier);
          testFailure(throwableOptional, messageName, childIdentifier, 0);
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
  
  private void testFailure(Optional<Throwable> failure, String messageName, TestIdentifier testIdentifier, long duration) {
    final Map<String, String> attrs = new HashMap<>();
    attrs.put("name", testIdentifier.getDisplayName());
    attrs.put("id", testIdentifier.getUniqueId().toString());
    if (duration > 0) {
      attrs.put("duration", Long.toString(duration));
    }
    try {
      if (failure.isPresent()) {
        final Throwable ex = failure.get();
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter writer = new PrintWriter(stringWriter);
        ex.printStackTrace(writer);
        ComparisonFailureData.registerSMAttributes(null, stringWriter.toString(), ex.getMessage(), attrs, ex);
      }
    }
    finally {
      myPrintStream.println("\n" + MapSerializerUtil.asString(messageName, attrs));
    }
  }


  public void sendTree(TestPlan testPlan, String rootName) {
    myRootName = rootName;
    for (TestIdentifier root : testPlan.getRoots()) {
      sendTreeUnderRoot(testPlan, root);
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

  private static String getClassName(TestIdentifier description) {
    Optional<JavaSource> javaSource = getJavaSource(description);
    return javaSource.map(source -> {
      final Optional<Class<?>> javaClass = source.getJavaClass();
      return javaClass.isPresent() ? javaClass.get().getName() : null;
    }).orElse(null);
  }

  private static String getMethodName(TestIdentifier testIdentifier) {
    return getJavaSource(testIdentifier).map((source) -> source.getJavaMethodName().orElse(null)).orElse(null);
  }

  private static Optional<JavaSource> getJavaSource(TestIdentifier testIdentifier) {
    return testIdentifier.getSource().filter(JavaSource.class::isInstance).map(JavaSource.class::cast);
  }
  
}
