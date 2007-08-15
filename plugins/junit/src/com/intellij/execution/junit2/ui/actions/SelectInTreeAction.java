package com.intellij.execution.junit2.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class SelectInTreeAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final TestContext context = TestContext.from(e);
    if (!shouldBeEnabled(context))
      return;
    context.getModel().selectTest(context.getSelection());
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(shouldBeEnabled(TestContext.from(e)));
  }

  private static boolean shouldBeEnabled(final TestContext context) {
    if (context == null || !context.hasSelection())
      return false;
    return context.treeContainsSelection();
  }
}
