package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.junit2.ui.model.CompletionEvent;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.StateEvent;
import com.intellij.execution.testframework.LvcsHelper;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.history.LocalHistoryConfiguration;

public class LvcsLabeler extends JUnitAdapter {
  private final TestFrameworkRunningModel myModel;

  public LvcsLabeler(final TestFrameworkRunningModel model) {
    myModel = model;
  }

  public void onRunnerStateChanged(final StateEvent event) {
    if (!(event instanceof CompletionEvent)) {
      return;
    }
    final boolean areTestsFailed = myModel.getRoot().isDefect();
    final CompletionEvent completion = (CompletionEvent)event;
    if (!needsLabel(areTestsFailed, completion.isNormalExit())) {
      return;
    }
    final RuntimeConfiguration configuration = myModel.getProperties().getConfiguration();
    if (configuration == null) {
      return;
    }
    if (testsTerminatedAndNotFailed(completion, areTestsFailed)) return;

    if (completion.isNormalExit()) {
      LvcsHelper.addLabel(myModel);
    }
  }


  private static boolean testsTerminatedAndNotFailed(final CompletionEvent completion, final boolean areTestsPassed) {
    return !completion.isNormalExit() && areTestsPassed;
  }

  private static boolean needsLabel(final boolean areTestsFailed, final boolean testsDone) {
    final LocalHistoryConfiguration config = LocalHistoryConfiguration.getInstance();
    if (config == null) {
      return false;
    }
    if (!testsDone) {
      return config.ADD_LABEL_ON_UNIT_TEST_FAILED;
    }
    return areTestsFailed ? config.ADD_LABEL_ON_UNIT_TEST_FAILED : config.ADD_LABEL_ON_UNIT_TEST_PASSED;
  }
}
