package com.intellij.execution.testframework.sm.runner.ui.statistics;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView;
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class ShowStatisticsAction extends AnAction {

  public void actionPerformed(final AnActionEvent e) {
    final SMTRunnerTestTreeView sender = e.getData(SMTRunnerTestTreeView.SM_TEST_RUNNER_VIEW);
    if (sender == null) {
      return;
    }

    final TestResultsViewer resultsViewer = sender.getResultsViewer();
    assert resultsViewer != null;

    resultsViewer.showStatisticsForSelectedProxy();
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    // visible only in SMTRunnerTestTreeView 
    presentation.setVisible(e.getData(SMTRunnerTestTreeView.SM_TEST_RUNNER_VIEW) != null);
    // enabled if some proxy is selected
    presentation.setEnabled(getSelectedTestProxy(e) != null);
  }

  @Nullable
  private Object getSelectedTestProxy(final AnActionEvent e) {
    return e.getDataContext().getData (AbstractTestProxy.DATA_CONSTANT);
  }
}
