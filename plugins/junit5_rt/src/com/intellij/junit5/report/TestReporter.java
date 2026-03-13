// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.report;

import com.intellij.junit4.ExpectedPatterns;
import com.intellij.rt.execution.junit.ComparisonFailureData;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestIdentifier;
import org.opentest4j.MultipleFailuresError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.rt.execution.junit.MapSerializerUtil.SUITE_TREE_NODE;
import static com.intellij.rt.execution.junit.MapSerializerUtil.TEST_FAILED;
import static com.intellij.rt.execution.junit.MapSerializerUtil.TEST_FINISHED;
import static com.intellij.rt.execution.junit.MapSerializerUtil.TEST_IGNORED;
import static com.intellij.rt.execution.junit.MapSerializerUtil.TEST_STARTED;
import static com.intellij.rt.execution.junit.MapSerializerUtil.asString;

public class TestReporter extends AbstractTestReporter {
  private final String name;

  public TestReporter(TestIdentifier identifier, ExecutionState state, String name) {
    super(identifier, state);
    this.name = name;
  }

  public TestReporter(TestIdentifier identifier, ExecutionState state) {
    this(identifier, state, identifier.getDisplayName());
  }

  @Override
  protected String name() {
    return name;
  }

  @Override
  public List<String> start() {
    state.onLeafTestStarted();
    String start = asString(TEST_STARTED, attributes(ReportedField.ID, ReportedField.NAME,
                                                     ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID,
                                                     ReportedField.HINT, ReportedField.METAINFO));
    return Collections.singletonList(start);
  }

  @Override
  public List<String> finish(TestExecutionResult result) {
    Throwable ex = result.getThrowable().orElse(null);
    long duration = state.onLeafTestFinishedAndGetDurationMs();

    List<String> out = new ArrayList<>();

    switch (result.getStatus()) {
      case ABORTED:
        out.addAll(reportFailure(ex, duration, true, TEST_IGNORED));
        break;
      case FAILED:
        out.addAll(reportFailure(ex, duration, true, TEST_FAILED));
        break;
      case SUCCESSFUL: // do nothing
        break;
    }

    Map<String, String> attributes = attributes(ReportedField.ID, ReportedField.NAME, ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID);
    if (duration > 0) {
      attributes.put("duration", Long.toString(duration));
    }
    out.add(asString(TEST_FINISHED, attributes));

    state.incrementFinishCount();
    return out;
  }

  @Override
  public List<String> treeStarted() {
    String started = asString(SUITE_TREE_NODE, attributes(ReportedField.ID, ReportedField.NAME,
                                                          ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID,
                                                          ReportedField.HINT, ReportedField.METAINFO
    ));
    return Collections.singletonList(started);
  }

  @Override
  public List<String> treeFinished() {
    return Collections.emptyList();
  }

  @Override
  public List<String> skip(String reason) {
    List<String> out = new ArrayList<>(reportFailure(null, 0, true, TEST_IGNORED, reason));
    out.add(asString(TEST_FINISHED, attributes(ReportedField.ID, ReportedField.NAME, ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID)));

    state.incrementFinishCount();
    return out;
  }

  @SuppressWarnings("SameParameterValue")
  private List<String> reportFailure(Throwable ex,
                                     long duration,
                                     boolean includeThrowable,
                                     String messageType) {
    return reportFailure(ex, duration, includeThrowable, messageType, null);
  }

  protected List<String> ignore(Throwable ex, String reason) {
    List<String> out = new ArrayList<>();
    out.add(asString(TEST_STARTED, attributes(ReportedField.ID, ReportedField.NAME,
                                              ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID,
                                              ReportedField.HINT, ReportedField.METAINFO)));
    out.addAll(reportFailure(ex, 0, true, TEST_IGNORED, reason));
    out.add(asString(TEST_FINISHED, attributes(ReportedField.ID, ReportedField.NAME,
                                               ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID)));
    return out;
  }

  List<String> reportFailure(Throwable ex,
                             long duration,
                             boolean includeThrowable,
                             String messageType, // TEST_FAILED or TEST_IGNORED
                             String reason) {
    Map<String, String> attributes = attributes(ReportedField.ID, ReportedField.NAME,
                                                ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID);

    if (duration > 0) {
      attributes.put("duration", Long.toString(duration));
    }
    if (reason != null) {
      attributes.put("message", reason);
    }

    List<String> out = new ArrayList<>();

    if (ex != null) {
      ComparisonFailureData failureData = null;
      if (ex instanceof MultipleFailuresError && ((MultipleFailuresError)ex).hasFailures()) {
        for (Throwable t : ((MultipleFailuresError)ex).getFailures()) {
          out.addAll(reportFailure(t, duration, true, messageType, reason));
        }
      }
      else {
        try {
          failureData = ExpectedPatterns.createExceptionNotification(ex);
        }
        catch (Throwable ignore) {
        }
      }

      boolean hasComparisonData = failureData != null || ex instanceof MultipleFailuresError;
      String trace = includeThrowable ? getTrace(ex) : "";
      ComparisonFailureData.registerSMAttributes(failureData, hasComparisonData ? "" : trace, ex.getMessage(), attributes, ex,
                                                 "Comparison Failure: ", "expected: <");
      if (hasComparisonData && !trace.isEmpty()) {
        attributes.put("details", trace);
      }
    }

    out.add(asString(messageType, attributes));
    return out;
  }
}