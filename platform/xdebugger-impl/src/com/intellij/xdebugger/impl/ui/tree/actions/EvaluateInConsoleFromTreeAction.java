package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.xdebugger.impl.actions.handlers.XEvaluateInConsoleFromEditorActionHandler;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class EvaluateInConsoleFromTreeAction extends XAddToWatchesAction {
  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return super.isEnabled(node, e) && getConsoleExecuteAction(e) != null;
  }

  @Nullable
  private static ConsoleExecuteAction getConsoleExecuteAction(@NotNull AnActionEvent e) {
    return XEvaluateInConsoleFromEditorActionHandler.getConsoleExecuteAction(e.getData(LangDataKeys.CONSOLE_VIEW));
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    ConsoleExecuteAction action = getConsoleExecuteAction(e);
    if (action != null) {
      String expression = node.getValueContainer().getEvaluationExpression();
      if (expression != null) {
        action.execute(null, expression, null);
      }
    }
  }
}