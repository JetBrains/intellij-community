// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.report;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface TeamCityTestReporter {
  List<String> start();

  List<String> output(ReportEntry entry);

  List<String> finish(TestExecutionResult result);
  
  List<String> skip(String reason);

  List<String> treeStarted();

  List<String> treeFinished();

  static AbstractTestReporter get(TestIdentifier identifier, ExecutionState state) {
    if (identifier.isTest() && identifier.isContainer()) {
      Set<TestIdentifier> descendants = state.plan() != null
                                        ? state.plan().getDescendants(identifier)
                                        : Collections.emptySet();
      return descendants.isEmpty()
             ? new TestReporter(identifier, state)
             : new SuiteReporter(identifier, state);
    }
    else if (identifier.isTest()) {
      return new TestReporter(identifier, state);
    }
    else {
      return new SuiteReporter(identifier, state);
    }
  }
}