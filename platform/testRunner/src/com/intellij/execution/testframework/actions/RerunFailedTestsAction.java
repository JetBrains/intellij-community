package com.intellij.execution.testframework.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

class RerunFailedTestsAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    throw new IllegalStateException("Action only as template");
  }
}
