// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.report;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;

import java.util.List;

public interface TeamCityTestReporter {
  List<String> start();

  List<String> output(ReportEntry entry);

  List<String> finish(TestExecutionResult result);
  
  List<String> skip(String reason);

  List<String> treeStarted();

  List<String> treeFinished();

  static AbstractTestReporter get(TestIdentifier identifier, ExecutionState state) {
    if (identifier.isTest() && !identifier.isContainer()) {
      return new TestReporter(identifier, state);
    }
    return new SuiteReporter(identifier, state);
  }
}