package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.util.OpenSourceUtil;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerUIActionsHandler implements TestResultsViewer.EventsListener {
  private final TestConsoleProperties myConsoleProperties;

  public SMTRunnerUIActionsHandler(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  //@Override
  //public void onTestFinished(@NotNull final SMTestProxy test) {
  //  if (!firstWasFound) {
  //    // select first defect on the fly
  //    if (test.isDefect()
  //        && TestConsoleProperties.SELECT_FIRST_DEFECT.value(myConsoleProperties)) {
  //
  //      myResultsViewer.selectAndNotify(test);
  //    }
  //    firstWasFound = true;
  //  }
  //}

  public void onTestNodeAdded(final TestResultsViewer sender, final SMTestProxy test) {
    if (TestConsoleProperties.TRACK_RUNNING_TEST.value(myConsoleProperties)) {
      sender.selectAndNotify(test);
    }
  }

  public void onTestingFinished(final TestResultsViewer sender) {
    // select first defect at the end (my be TRACK_RUNNING_TEST was enabled and affects on the fly selection)
    final SMTestProxy testsRootNode = sender.getTestsRootNode();
    if (TestConsoleProperties.SELECT_FIRST_DEFECT.value(myConsoleProperties)) {
      final AbstractTestProxy firstDefect =
          Filter.DEFECTIVE_LEAF.detectIn(testsRootNode.getAllTests());
      if (firstDefect != null) {
        sender.selectAndNotify(firstDefect);
      }
    }
  }

  public void onSelected(@Nullable final PrintableTestProxy selectedTestProxy,
                         @NotNull final TestResultsViewer viewer,
                         @NotNull final TestFrameworkRunningModel model) {
    //TODO: tests o "onSelected"
    SMRunnerUtil.runInEventDispatchThread(new Runnable() {
      public void run() {
        if (ScrollToTestSourceAction.isScrollEnabled(model)) {
          OpenSourceUtil.openSourcesFrom(model.getTreeView(), false);
        }
      }
    }, ModalityState.NON_MODAL);
  }
}
