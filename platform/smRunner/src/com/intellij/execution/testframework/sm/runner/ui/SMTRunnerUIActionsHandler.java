// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ProxyFilters;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerUIActionsHandler implements TestResultsViewer.EventsListener {
  private final TestConsoleProperties myConsoleProperties;

  public SMTRunnerUIActionsHandler(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  @Override
  public void onTestingFinished(final @NotNull TestResultsViewer sender) {
    // select first defect at the end (my be TRACK_RUNNING_TEST was enabled and affects on the fly selection)
    final SMTestProxy testsRootNode = sender.getTestsRootNode();
    if (TestConsoleProperties.SELECT_FIRST_DEFECT.value(myConsoleProperties)) {
      final AbstractTestProxy firstDefect;

      // defects priority:
      // ERROR -> FAILURE -> GENERAL DEFECTIVE NODE
      final List<SMTestProxy> allTests = testsRootNode.getAllTests();
      final AbstractTestProxy firstError = ProxyFilters.ERROR_LEAF.detectIn(allTests);
      if (firstError != null) {
        firstDefect = firstError;
      }
      else {
        final AbstractTestProxy firstFailure = ProxyFilters.FAILURE_LEAF.detectIn(allTests);
        if (firstFailure != null) {
          firstDefect = firstFailure;
        }
        else {
          firstDefect = null;
        }
      }

      // select if detected
      if (firstDefect != null) {
        sender.selectAndNotify(firstDefect);
      }
    }
  }
}
