// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit5;

import com.intellij.junit4.ExpectedPatterns;
import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
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

import static com.intellij.rt.execution.TestListenerProtocol.CLASS_CONFIGURATION;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class JUnit5TestExecutionListener implements TestExecutionListener {
  private static final String NO_LOCATION_HINT = "";
  private static final String NO_LOCATION_HINT_VALUE = "";
  private final PrintStream myPrintStream;
  private TestPlan myTestPlan;
  private long myCurrentTestStart;
  private int myFinishCount = 0;
  private String myRootName;
  private boolean mySuccessful = true;
  private String myIdSuffix = "";
  private final Set<TestIdentifier> myActiveRoots = new LinkedHashSet<>();
  private boolean mySendTree;

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

  public void initializeIdSuffix(boolean forked) {
    if (forked && myIdSuffix.length() == 0) {
      myIdSuffix = String.valueOf(System.currentTimeMillis());
    }
  }
  
  public void initializeIdSuffix(int i) {
    myIdSuffix = i + "th"; 
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
    if (mySendTree) {
      for (TestIdentifier root : myTestPlan.getRoots()) {
        assert root.isContainer();
        for (TestIdentifier testIdentifier : myTestPlan.getChildren(root)) {
          sendTreeUnderRoot(testIdentifier, new HashSet<>());
        }
      }
      myPrintStream.println("##teamcity[treeEnded]");
    }

    if (myRootName != null) {
      int lastPointIdx = myRootName.lastIndexOf('.');
      String name = myRootName;
      String comment = null;
      if (lastPointIdx >= 0) {
        name = myRootName.substring(lastPointIdx + 1);
        comment = myRootName.substring(0, lastPointIdx);
      }

      myPrintStream.println("##teamcity[rootName name = '" + escapeName(name) +
                            (comment != null ? ("' comment = '" + escapeName(comment)) : "") + "'" +
                            " location = 'java:suite://" + escapeName(myRootName) +
                            "']");
    }
  }

  private void sendTreeUnderRoot(TestIdentifier root,
                                 HashSet<TestIdentifier> visited) {
    final String idAndName = idAndName(root);
    if (root.isContainer()) {
      boolean skipContainer = shouldSkipContainer(root);
      if (!skipContainer) myPrintStream.println("##teamcity[suiteTreeStarted" + idAndName + " " + getLocationHint(root) + "]");
      for (TestIdentifier childIdentifier : myTestPlan.getChildren(root)) {
        if (visited.add(childIdentifier)) {
          sendTreeUnderRoot(childIdentifier, visited);
        }
        else {
          System.err.println("Identifier '" + getId(childIdentifier) + "' is reused");
        }
      }
      if (!skipContainer) myPrintStream.println("##teamcity[suiteTreeEnded" + idAndName + "]");
    }
    else if (root.isTest()) {
      myPrintStream.println("##teamcity[suiteTreeNode " + idAndName + " " + getLocationHint(root) + "]");
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
    else if (!shouldSkipContainer(testIdentifier)) {
      myFinishCount = 0;
      myPrintStream.println("##teamcity[testSuiteStarted" + idAndName(testIdentifier) + getLocationHint(testIdentifier) + "]");
    }
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
    else if (!shouldSkipContainer(testIdentifier)){
      String messageName = null;
      if (status == TestExecutionResult.Status.FAILED) {
        messageName = MapSerializerUtil.TEST_FAILED;
      }
      else if (status == TestExecutionResult.Status.ABORTED) {
        messageName = MapSerializerUtil.TEST_IGNORED;
      }
      if (messageName != null) {
        final Set<TestIdentifier> descendants = myTestPlan != null ? myTestPlan.getDescendants(testIdentifier) : Collections.emptySet();
        if (status == TestExecutionResult.Status.FAILED) {
          String parentId = getParentId(testIdentifier);
          String nameAndId = " name='" + CLASS_CONFIGURATION +
                             "' nodeId='" + escapeName(getId(testIdentifier)) +
                             "' parentNodeId='" + escapeName(parentId) + "' ";
          testFailure(CLASS_CONFIGURATION, getId(testIdentifier), parentId, messageName, throwableOptional, 0, reason, true);
          myPrintStream.println("##teamcity[testFinished" + nameAndId + "]");
        }
        else if (status == TestExecutionResult.Status.ABORTED && descendants.isEmpty()) {
          testFailure(testIdentifier, MapSerializerUtil.TEST_IGNORED, throwableOptional, 0, reason, true);
        }

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

  private boolean shouldSkipContainer(TestIdentifier testIdentifier) {
    UniqueId id = UniqueId.parse(testIdentifier.getUniqueId());
    List<UniqueId.Segment> segments = id.getSegments();
    if (segments.isEmpty()) return false;
    UniqueId.Segment lastSegment = segments.get(segments.size() - 1);
    return lastSegment.getType().equals("engine") || 
           myRootName != null && myRootName.equals(lastSegment.getValue());
  }

  protected long getDuration() {
    return System.currentTimeMillis() - myCurrentTestStart;
  }

  private void testStarted(TestIdentifier testIdentifier) {
    myPrintStream.println("##teamcity[testStarted" + idAndName(testIdentifier) + " " + getLocationHint(testIdentifier) + "]");
  }
  
  private void testFinished(TestIdentifier testIdentifier, long duration) {
    myPrintStream.println("##teamcity[testFinished" + idAndName(testIdentifier) + (duration > 0 ? " duration='" + duration + "'" : "") + "]");
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
          ComparisonFailureData.registerSMAttributes(failureData, "", ex.getMessage(), attrs, ex, "Comparison Failure: ", "expected: <");
        }
      }
    }
    finally {
      myPrintStream.println(MapSerializerUtil.asString(messageName, attrs));
    }
  }

  protected String getTrace(Throwable ex) {
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(stringWriter);
    ex.printStackTrace(writer);
    return stringWriter.toString();
  }

  public void setRootName(String rootName) {
    myRootName = rootName;
  }

  void setSendTree() {
    mySendTree = true;
  }

  private String getId(TestIdentifier identifier) {
    return identifier.getUniqueId() + myIdSuffix;
  }

  private String idAndName(TestIdentifier testIdentifier) {
    return idAndName(testIdentifier, testIdentifier.getDisplayName());
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
      .map(identifier -> shouldSkipContainer(identifier) ? getParentId(identifier) : identifier.getUniqueId() + myIdSuffix)
      .orElse("0");
  }

  
  private String getLocationHint(TestIdentifier root) {
    return getLocationHint(root, myTestPlan.getParent(root).orElse(null));
  }

  static String getLocationHint(TestIdentifier root, final TestIdentifier rootParent) {
    return root.getSource()
      .map(testSource -> getLocationHintValue(testSource, rootParent != null ? rootParent.getSource().orElse(null) : null))
      .filter(maybeLocationHintValue -> !NO_LOCATION_HINT_VALUE.equals(maybeLocationHintValue))
      .map(locationHintValue -> "locationHint='" + locationHintValue + "'" + getMetainfo(root))
      .orElse(NO_LOCATION_HINT);
  }

  private static String getMetainfo(TestIdentifier root) {
    return root.getSource()
      .map(testSource -> {
        if (testSource instanceof MethodSource) {
          return " metainfo='" + ((MethodSource)testSource).getMethodParameterTypes() + "'";
        }
        if (testSource instanceof ClassSource) {
          return ((ClassSource)testSource).getPosition()
            .map(position -> " metainfo='" + position.getLine() + ":" + position.getColumn() + "'")
            .orElse(NO_LOCATION_HINT);
        }
        return NO_LOCATION_HINT;
      })
      .orElse(NO_LOCATION_HINT);
  }
  
  static String getLocationHintValue(TestSource testSource, TestSource parentSource) {

    if (testSource instanceof CompositeTestSource) {
      CompositeTestSource compositeTestSource = ((CompositeTestSource)testSource);
      for (TestSource sourceFromComposite : compositeTestSource.getSources()) {
        String locationHintValue = getLocationHintValue(sourceFromComposite, parentSource);
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

    if (parentSource != null) {
      return getLocationHintValue(parentSource,null);
    }

    return NO_LOCATION_HINT_VALUE;
  }

  private static String javaLocation(String className, String maybeMethodName, boolean isTest) {
    String type = isTest ? "test" : "suite";
    String methodName = maybeMethodName == null ? "" : "/" + maybeMethodName;
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
