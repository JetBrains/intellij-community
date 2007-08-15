package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NonNls;

public class TestContext {
  @NonNls public static final String TEST_CONTEXT = "JUNIT_CONTEXT";

  private final JUnitRunningModel myModel;
  private final TestProxy mySelection;

  public TestContext(final JUnitRunningModel model, final TestProxy selection) {
    myModel = model;
    mySelection = selection;
  }

  public JUnitRunningModel getModel() {
    return myModel;
  }

  public TestProxy getSelection() {
    return mySelection;
  }

  public boolean hasSelection() {
    return getSelection() != null && getModel() != null;
  }

  public boolean treeContainsSelection() {
    return getModel().hasInTree(getSelection());
  }

  public static TestContext from(final AnActionEvent event) {
    return (TestContext) event.getDataContext().getData(TEST_CONTEXT);
  }
}
