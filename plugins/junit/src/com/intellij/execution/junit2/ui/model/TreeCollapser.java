package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.NewChildEvent;
import com.intellij.execution.junit2.TestEvent;
import com.intellij.execution.junit2.TestProxy;

public class TreeCollapser extends JUnitAdapter {
  private JUnitRunningModel myModel;
  private TestProxy myLastDynamicSuite = null;

  public void setModel(final JUnitRunningModel model) {
    myModel = model;
    model.addListener(this);
  }

  public void onTestChanged(final TestEvent event) {
    if (!(event instanceof NewChildEvent))
      return;
    final TestProxy parent = event.getSource();
    if (parent == myLastDynamicSuite)
      return;
    if (parent.getParent() != myModel.getRoot())
      return;
    if (myLastDynamicSuite != null && myLastDynamicSuite.getState().isPassed())
      myModel.collapse(myLastDynamicSuite);
    myLastDynamicSuite = parent;
  }

  public void doDispose() {
    myModel = null;
    myLastDynamicSuite = null;
  }
}
