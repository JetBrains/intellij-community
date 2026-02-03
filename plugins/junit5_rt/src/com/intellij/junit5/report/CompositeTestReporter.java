// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.report;

import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;

import java.util.ArrayList;
import java.util.List;

// e.g., parametrized test
public class CompositeTestReporter extends AbstractTestReporter {
  private final TestReporter asTest;
  private final SuiteReporter asSuite;

  public CompositeTestReporter(TestIdentifier identifier, ExecutionState state) {
    super(identifier, state);
    asTest = new TestReporter(identifier, state);
    asSuite = new SuiteReporter(identifier, state);
  }

  @Override
  public List<String> start() {
    List<String> out = new ArrayList<>();
    out.addAll(asSuite.start());
    out.addAll(asTest.start());
    return out;
  }

  @Override
  public List<String> output(ReportEntry entry) {
    return asTest.output(entry);
  }

  @Override
  public List<String> finish(TestExecutionResult result) {
    List<String> out = new ArrayList<>();
    out.addAll(asTest.finish(result));
    out.addAll(asSuite.finish(result));
    return out;
  }

  @Override
  public List<String> treeStarted() {
    List<String> out = new ArrayList<>(asSuite.treeStarted());
    String testNode = MapSerializerUtil.asString(MapSerializerUtil.SUITE_TREE_NODE,
                                                 attributes(ReportedField.ID, ReportedField.NAME,
                                                            ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID,
                                                            ReportedField.HINT, ReportedField.METAINFO));
    out.add(testNode);
    return out;
  }

  @Override
  public List<String> treeFinished() {
    return asSuite.treeFinished();
  }

  @Override
  public List<String> skip(String reason) {
    return asTest.skip(reason);
  }
}