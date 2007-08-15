package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.StateEvent;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.actions.TestFrameworkActions;

public class JUnitActions extends TestFrameworkActions {
  public static void installAutoscrollToFirstDefect(final JUnitRunningModel model) {
    model.addListener(new JUnitAdapter() {
      public void onRunnerStateChanged(final StateEvent event) {
        if (event.isRunning() || !shouldSelect())
          return;
        final AbstractTestProxy firstDefect = Filter.DEFECTIVE_LEAF.detectIn(model.getRoot().getAllTests());
        if (firstDefect != null) model.selectAndNotify(firstDefect);
      }

      private boolean shouldSelect() {
        return JUnitConsoleProperties.SELECT_FIRST_DEFECT.value(model.getProperties());
      }
    });
  }
}
