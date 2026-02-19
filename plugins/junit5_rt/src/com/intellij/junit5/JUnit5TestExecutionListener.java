// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.junit5.report.ExecutionState;
import com.intellij.junit5.report.TeamCityTestReporter;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class JUnit5TestExecutionListener implements TestExecutionListener {
  private final ExecutionState myState;

  public JUnit5TestExecutionListener() {
    this(System.out);
  }

  public JUnit5TestExecutionListener(PrintStream printStream) {
    myState = new ExecutionState(printStream);
    myState.print("##teamcity[enteredTheMatrix]");
  }

  public boolean wasSuccessful() {
    return myState.wasSuccessful();
  }

  public void initializeIdSuffix(boolean forked) {
    myState.initializeIdSuffix(forked);
  }

  public void initializeIdSuffix(int i) {
    myState.initializeIdSuffix(i);
  }

  public void setRootName(String rootName) {
    myState.setRootName(rootName);
  }

  public void setPresentableName(String presentableName) {
    myState.setPresentableName(presentableName);
  }

  public void setSendTree() {
    myState.setSendTree(true);
  }

  @Override
  public void testPlanExecutionStarted(TestPlan testPlan) {
    myState.setPlan(testPlan);

    if (myState.isSendTree()) {
      for (TestIdentifier root : testPlan.getRoots()) {
        assert root.isContainer();

        for (TestIdentifier testIdentifier : testPlan.getChildren(root)) {
          String legacyReportingName = testIdentifier.getLegacyReportingName();
          if (legacyReportingName != null && legacyReportingName.equals(myState.getRootName())) {
            myState.setPresentableName(testIdentifier.getDisplayName());
          }
          sendTreeUnderRoot(testIdentifier, new HashSet<>());
        }
      }
      myState.print("##teamcity[treeEnded]");
    }
    myState.printRootNameIfNeeded();
  }

  public void sendTreeUnderRoot(TestIdentifier root, Set<TestIdentifier> visited) {
    TeamCityTestReporter reporter = TeamCityTestReporter.get(root, myState);

    reporter.treeStarted().forEach(myState::print);
    for (TestIdentifier child : myState.plan().getChildren(root)) {
      if (visited.add(child)) {
        sendTreeUnderRoot(child, visited);
      }
      else {
        System.err.println("Identifier '" + child.getUniqueId() + "' is reused");
      }
    }
    reporter.treeFinished().forEach(myState::print);
  }

  @Override
  public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
    TeamCityTestReporter.get(testIdentifier, myState).output(entry)
      .forEach(myState::print);
  }

  @Override
  public void executionSkipped(TestIdentifier testIdentifier, String reason) {
    executionStarted(testIdentifier);
    TeamCityTestReporter.get(testIdentifier, myState)
      .skip(reason)
      .forEach(myState::print);
  }

  @Override
  public void executionStarted(TestIdentifier testIdentifier) {
    TeamCityTestReporter.get(testIdentifier, myState).start()
      .forEach(myState::print);
  }

  @Override
  public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
    TeamCityTestReporter.get(testIdentifier, myState).finish(testExecutionResult)
      .forEach(myState::print);
    myState.updateSuccessful(testExecutionResult.getStatus());
  }

  public static String getClassName(TestIdentifier description) {
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

  public static String getMethodName(TestIdentifier testIdentifier) {
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
