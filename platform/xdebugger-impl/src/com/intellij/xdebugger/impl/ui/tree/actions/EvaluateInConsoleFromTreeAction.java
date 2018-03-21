// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.xdebugger.impl.actions.handlers.XEvaluateInConsoleFromEditorActionHandler;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class EvaluateInConsoleFromTreeAction extends XAddToWatchesTreeAction {
  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return super.isEnabled(node, e) && getConsoleExecuteAction(e) != null;
  }

  @Override
  public void update(AnActionEvent e) {
    if (getConsoleExecuteAction(e) != null) {
      e.getPresentation().setVisible(true);
      super.update(e);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Nullable
  private static ConsoleExecuteAction getConsoleExecuteAction(@NotNull AnActionEvent e) {
    return XEvaluateInConsoleFromEditorActionHandler.getConsoleExecuteAction(e.getData(LangDataKeys.CONSOLE_VIEW));
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    final ConsoleExecuteAction action = getConsoleExecuteAction(e);
    if (action != null) {
      node.getValueContainer().calculateEvaluationExpression()
          .onSuccess(expression -> {
            if (expression != null) {
              action.execute(null, expression.getExpression(), null);
            }
          });
    }
  }
}