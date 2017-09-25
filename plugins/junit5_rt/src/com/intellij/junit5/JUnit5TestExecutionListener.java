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
import com.intellij.junit4.JUnit4TestListener;
import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.CompositeTestSource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;
import org.opentest4j.ValueWrapper;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class JUnit5TestExecutionListener implements TestExecutionListener {
  private static final String NO_LOCATION_HINT = "";
  private static final String NO_LOCATION_HINT_VALUE = "";
  private final PrintStream myPrintStream;
  private TestPlan myTestPlan;
  private long myCurrentTestStart;
  private int myFinishCount;
  private String myRootName;
  private boolean mySuccessful;
  private String myForkModifier = "";

  public JUnit5TestExecutionListener() {
    this(System.out);
  }

  public JUnit5TestExecutionListener(PrintStream printStream) {
    myPrintStream = printStream;
    myPrintStream.println("##teamcity[enteredTheMatrix]");
  }

  public boolean wasSuccessful() {
    return mySuccessful;
  }

  public void initialize(boolean forked) {
    mySuccessful = true;
    myFinishCount = 0;
    if (forked) {
      myForkModifier = String.valueOf(System.currentTimeMillis());
    }
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
    else if (testIdentifier.getParentId().isPresent()) {
      myFinishCount = 0;
      myPrintStream.println("##teamcity[testSuiteStarted" + idAndName(testIdentifier) + getLocationHint(testIdentifier) + "]");
    }
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
    mySuccessful &= TestExecutionResult.Status.SUCCESSFUL == testExecutionResult.getStatus();
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
    else if (testIdentifier.getParentId().isPresent()){
      String messageName = null;
      if (status == TestExecutionResult.Status.FAILED) {
        messageName = MapSerializerUtil.TEST_FAILED;
      }
      else if (status == TestExecutionResult.Status.ABORTED) {
        messageName = MapSerializerUtil.TEST_IGNORED;
      }
      if (messageName != null) {
        if (status == TestExecutionResult.Status.FAILED) {
          String parentId = getParentId(testIdentifier);
          String nameAndId = " name=\'" + JUnit4TestListener.CLASS_CONFIGURATION +
                             "\' nodeId=\'" + escapeName(getId(testIdentifier)) +
                             "\' parentNodeId=\'" + parentId + "\' ";
          testFailure(JUnit4TestListener.CLASS_CONFIGURATION, getId(testIdentifier), parentId, messageName, throwableOptional, 0, reason, true);
          myPrintStream.println("\n##teamcity[testFinished" + nameAndId + "]");
        }

        final Set<TestIdentifier> descendants = myTestPlan != null ? myTestPlan.getDescendants(testIdentifier) : Collections.emptySet();
        if (!descendants.isEmpty() && myFinishCount == 0) {
          for (TestIdentifier childIdentifier : descendants) {
            testStarted(childIdentifier);
            testFailure(childIdentifier, MapSerializerUtil.TEST_IGNORED, status == TestExecutionResult.Status.ABORTED ? throwableOptional : null, 0, reason, status == TestExecutionResult.Status.ABORTED);
            testFinished(childIdentifier, 0);
          }
          myFinishCount = 0;
        }
      }
      myPrintStream.println("##teamcity[testSuiteFinished " + idAndName(testIdentifier, displayName) + "]");
    }
  }

  protected long getDuration() {
    return System.currentTimeMillis() - myCurrentTestStart;
  }

  private void testStarted(TestIdentifier testIdentifier) {
    myPrintStream.println("\n##teamcity[testStarted" + idAndName(testIdentifier) + " " + getLocationHint(testIdentifier) + "]");
  }
  
  private void testFinished(TestIdentifier testIdentifier, long duration) {
    myPrintStream.println("\n##teamcity[testFinished" + idAndName(testIdentifier) + (duration > 0 ? " duration=\'" + Long.toString(duration) + "\'" : "") + "]");
  }

  private void testFailure(TestIdentifier testIdentifier,
                           String messageName,
                           Throwable ex,
                           long duration,
                           String reason,
                           boolean includeThrowable) {
    testFailure(testIdentifier.getDisplayName(), getId(testIdentifier), getParentId(testIdentifier), messageName, ex, duration, reason, includeThrowable);
  }

  private void testFailure(String methodName,
                           String id,
                           String parentId,
                           String messageName,
                           Throwable ex,
                           long duration,
                           String reason,
                           boolean includeThrowable) {
    final Map<String, String> attrs = new LinkedHashMap<>();
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
    try {
      if (ex != null) {
        ComparisonFailureData failureData = null;
        if (ex instanceof MultipleFailuresError && ((MultipleFailuresError)ex).hasFailures()) {
          for (Throwable assertionError : ((MultipleFailuresError)ex).getFailures()) {
            testFailure(methodName, id, parentId, messageName, assertionError, duration, reason, false);
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
          ComparisonFailureData.registerSMAttributes(failureData, getTrace(ex), ex.getMessage(), attrs, ex, "Comparison Failure: ", "expected: <");
        }
        else {
          ComparisonFailureData.registerSMAttributes(failureData, "", "", attrs, ex, "", "expected: <");
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

  public void setTestPlan(TestPlan testPlan) {
    myTestPlan = testPlan;
  }

  public void sendTree(TestPlan testPlan, String rootName) {
    myTestPlan = testPlan;
    myRootName = rootName;
    Set<TestIdentifier> roots = testPlan.getRoots();
    for (TestIdentifier root : roots) {
      assert root.isContainer();
      for (TestIdentifier testIdentifier : testPlan.getChildren(root)) {
        sendTreeUnderRoot(testPlan, testIdentifier, new HashSet<>());
      }
    }
    myPrintStream.println("##teamcity[treeEnded]");
  }

  private String getId(TestIdentifier identifier) {
    return identifier.getUniqueId() + myForkModifier;
  }

  private void sendTreeUnderRoot(TestPlan testPlan,
                                 TestIdentifier root,
                                 HashSet<TestIdentifier> visited) {
    final String idAndName = idAndName(root);
    if (root.isContainer()) {
      myPrintStream.println("##teamcity[suiteTreeStarted" + idAndName + " " + getLocationHint(root) + "]");
      for (TestIdentifier childIdentifier : testPlan.getChildren(root)) {
        if (visited.add(childIdentifier)) {
          sendTreeUnderRoot(testPlan, childIdentifier, visited);
        }
        else {
          System.err.println("Identifier \'" + getId(childIdentifier) + "\' is reused");
        }
      }
      myPrintStream.println("##teamcity[suiteTreeEnded" + idAndName + "]");
    }
    else if (root.isTest()) {
      myPrintStream.println("##teamcity[suiteTreeNode " + idAndName + " " + getLocationHint(root) + "]");
    }
  }

  private String idAndName(TestIdentifier testIdentifier) {
    return idAndName(testIdentifier, testIdentifier.getDisplayName());
  }

  private String idAndName(TestIdentifier testIdentifier, String displayName) {
    return " id=\'" + escapeName(getId(testIdentifier)) +
           "\' name=\'" + escapeName(displayName) +
           "\' nodeId=\'" + escapeName(getId(testIdentifier)) +
           "\' parentNodeId=\'" + escapeName(getParentId(testIdentifier)) + "\'";
  }

  private String getParentId(TestIdentifier testIdentifier) {
    Optional<TestIdentifier> parent = myTestPlan.getParent(testIdentifier);
    if (!parent.map(identifier -> identifier.getParentId().orElse(null)).isPresent()) {
      return "0";
    }

    return parent
      .map(identifier -> identifier.getUniqueId() + myForkModifier)
      .orElse("0");
  }

  static String getLocationHint(TestIdentifier root) {
    return root.getSource()
      .map(testSource -> getLocationHintValue(testSource))
      .filter(maybeLocationHintValue -> !NO_LOCATION_HINT_VALUE.equals(maybeLocationHintValue))
      .map(locationHintValue -> "locationHint=\'" + locationHintValue + "\'")
      .orElse(NO_LOCATION_HINT);
  }

  static String getLocationHintValue(TestSource testSource) {

    if (testSource instanceof CompositeTestSource) {
      CompositeTestSource compositeTestSource = ((CompositeTestSource)testSource);
      for (TestSource sourceFromComposite : compositeTestSource.getSources()) {
        String locationHintValue = getLocationHintValue(sourceFromComposite);
        if (!NO_LOCATION_HINT_VALUE.equals(locationHintValue)) {
          return locationHintValue;
        }
      }
      return NO_LOCATION_HINT_VALUE;
    }

    if (testSource instanceof FileSource) {
      FileSource fileSource = (FileSource)testSource;
      File file = fileSource.getFile();
      String line = fileSource.getPosition()
        .map(position -> ":" + position.getLine())
        .orElse("");
      return "file://" + file.getAbsolutePath() + line;
    }

    if (testSource instanceof MethodSource) {
      MethodSource methodSource = (MethodSource)testSource;
      return javaLocation(methodSource.getClassName(), methodSource.getMethodName(), true);
    }

    if (testSource instanceof ClassSource) {
      String className = ((ClassSource)testSource).getClassName();
      return javaLocation(className, null, false);
    }

    return NO_LOCATION_HINT_VALUE;
  }

  private static String javaLocation(String className, String maybeMethodName, boolean isTest) {
    String type = isTest ? "test" : "suite";
    String methodName = maybeMethodName == null ? "" : "." + maybeMethodName;
    String location = escapeName(className + methodName);
    return "java:" + type + "://" + location;
  }

  private static String escapeName(String str) {
    return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
  }

  static String getClassName(TestIdentifier description) {
    return description.getSource().map(source -> {
      if (source instanceof MethodSource) {
        return ((MethodSource)source).getClassName();
      }
      if (source instanceof ClassSource) {
        return ((ClassSource)source).getClassName();
      }
      return null;
    }).orElse(null);
  }

  static String getMethodName(TestIdentifier testIdentifier) {
    return testIdentifier.getSource().map((source) -> {
      if (source instanceof MethodSource) {
        return ((MethodSource)source).getMethodName();
      }
      return null;
    }).orElse(null);
  }
  
  static String getMethodSignature(TestIdentifier testIdentifier) {
    return testIdentifier.getSource().map((source) -> {
      if (source instanceof MethodSource) {
        String parameterTypes = ((MethodSource)source).getMethodParameterTypes();
        return ((MethodSource)source).getMethodName() + (parameterTypes != null ? "(" + parameterTypes + ")" : "");
      }
      return null;
    }).orElse(null);
  }
}
