// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.report;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.util.*;

import static com.intellij.rt.execution.TestListenerProtocol.CLASS_CONFIGURATION;
import static com.intellij.rt.execution.junit.MapSerializerUtil.*;

public class SuiteReporter extends AbstractTestReporter {
  public SuiteReporter(TestIdentifier identifier, ExecutionState state) {
    super(identifier, state);
  }

  // only for suites
  public boolean isSkipped() {
    UniqueId id = UniqueId.parse(identifier.getUniqueId());
    List<UniqueId.Segment> segments = id.getSegments();
    if (segments.isEmpty()) return false;

    UniqueId.Segment lastSegment = segments.get(segments.size() - 1);
    if ("engine".equals(lastSegment.getType())) return true;

    String root = state.getRootName();
    return root != null && root.equals(lastSegment.getValue());
  }

  @Override
  public List<String> start() {
    if (isSkipped()) return Collections.emptyList();

    state.resetFinishCount();
    String start = asString(TEST_SUITE_STARTED, attributes(ReportedField.ID, ReportedField.NAME, ReportedField.NODE_ID,
                                                           ReportedField.PARENT_NODE_ID, ReportedField.HINT, ReportedField.METAINFO));
    return Collections.singletonList(start);
  }

  @Override
  public List<String> finish(TestExecutionResult result) {
    if (isSkipped() && result.getStatus() != TestExecutionResult.Status.FAILED) {
      return Collections.emptyList();
    }

    List<String> out = new ArrayList<>();
    TestExecutionResult.Status status = result.getStatus();
    Throwable throwable = result.getThrowable().orElse(null);
    Set<TestIdentifier> descendants = state.plan() != null ? state.plan().getDescendants(identifier) : Collections.emptySet();

    if (status == TestExecutionResult.Status.FAILED) {
      // Report class-level failure as CLASS_CONFIGURATION test
      TestReporter reporter = new SyntheticTestReporter(this, CLASS_CONFIGURATION);
      out.add(asString(TEST_STARTED, reporter.attributes(ReportedField.ID, ReportedField.NAME,
                                                         ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID,
                                                         ReportedField.HINT, ReportedField.METAINFO)));
      out.addAll(reporter.reportFailure(throwable, 0, true, TEST_FAILED, null));
      out.add(asString(TEST_FINISHED, reporter.attributes(ReportedField.ID, ReportedField.NAME,
                                                          ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID)));
    }
    else if (status == TestExecutionResult.Status.ABORTED) {
      if (descendants.isEmpty()) {
        // Report as ignored
        Map<String, String> attrs = attributes(ReportedField.ID, ReportedField.NAME,
                                               ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID);
        if (throwable != null) {
          attrs.put("message", throwable.getMessage());
        }
        out.add(asString(TEST_IGNORED, attrs));
      }
    }

    if (!descendants.isEmpty() && state.finishCount() == 0) {
      String reason = (throwable != null) ? throwable.getMessage() : null;
      for (TestIdentifier child : descendants) {
        AbstractTestReporter reporter = TeamCityTestReporter.get(child, state);
        if (reporter instanceof TestReporter) {
          Throwable childEx = (status == TestExecutionResult.Status.ABORTED) ? throwable : null;
          out.addAll(((TestReporter)reporter).ignore(childEx, reason));
        }
      }
      state.resetFinishCount();
    }

    if (!isSkipped()) {
      out.add(asString(TEST_SUITE_FINISHED, attributes(ReportedField.ID, ReportedField.NAME,
                                                       ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID)));
    }

    return out;
  }

  @Override
  public List<String> treeStarted() {
    if (isSkipped()) return Collections.emptyList();

    String started = asString(SUITE_TREE_STARTED, attributes(ReportedField.ID, ReportedField.NAME,
                                                             ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID,
                                                             ReportedField.HINT, ReportedField.METAINFO
    ));
    return Collections.singletonList(started);
  }

  @Override
  public List<String> treeFinished() {
    if (isSkipped()) return Collections.emptyList();
    String finished = asString(SUITE_TREE_ENDED, attributes(ReportedField.ID, ReportedField.NAME,
                                                            ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID));
    return Collections.singletonList(finished);
  }

  @Override
  public List<String> skip(String reason) {
    List<String> out = new ArrayList<>();
    Set<TestIdentifier> descendants = state.plan() != null ? state.plan().getDescendants(identifier) : Collections.emptySet();

    if (descendants.isEmpty()) {
      Map<String, String> attrs = attributes(ReportedField.ID, ReportedField.NAME,
                                             ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID);
      if (reason != null && !reason.isEmpty()) attrs.put("message", reason);
      out.add(asString(TEST_IGNORED, attrs));
    } else {
      for (TestIdentifier child : descendants) {
        AbstractTestReporter reporter = TeamCityTestReporter.get(child, state);
        out.addAll(reporter.start());
        out.addAll(reporter.skip(reason));
      }
      state.resetFinishCount();
    }

    if (!isSkipped()) {
      out.add(asString(TEST_SUITE_FINISHED, attributes(ReportedField.ID, ReportedField.NAME,
                                                       ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID)));
    }

    return out;
  }

  private static class SyntheticTestReporter extends TestReporter {
    private final SuiteReporter myOriginal;
    private final boolean isMethodSource;

    SyntheticTestReporter(SuiteReporter original, String name) {
      super(original.identifier, original.state, name);
      myOriginal = original;
      isMethodSource = identifier.getSource().map(s -> s instanceof MethodSource).orElse(false);
    }

    @Override
    protected String id() {
      if (isMethodSource) return super.id();
      return super.id() + "/[synthetic:method:configuration()]";
    }

    @Override
    protected Optional<SuiteReporter> getParent() {
      // suite doesn't report for skipped classes
      if (isMethodSource || myOriginal.isSkipped()) return super.getParent();
      return Optional.of(myOriginal);
    }
  }
}