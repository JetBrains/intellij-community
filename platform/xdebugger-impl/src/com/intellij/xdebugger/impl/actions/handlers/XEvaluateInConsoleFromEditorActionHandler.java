package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class XEvaluateInConsoleFromEditorActionHandler extends XAddToWatchesFromEditorActionHandler {
  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, DataContext dataContext) {
    return super.isEnabled(session, dataContext) && getConsoleExecuteAction(session) != null;
  }

  @Nullable
  private static ConsoleExecuteAction getConsoleExecuteAction(@NotNull XDebugSession session) {
    return getConsoleExecuteAction(session.getConsoleView());
  }

  @Nullable
  public static ConsoleExecuteAction getConsoleExecuteAction(@Nullable ConsoleView consoleView) {
    if (!(consoleView instanceof LanguageConsoleView)) {
      return null;
    }

    List<AnAction> actions = ActionUtil.getActions(((LanguageConsoleView)consoleView).getConsoleEditor().getComponent());
    ConsoleExecuteAction action = ContainerUtil.findInstance(actions, ConsoleExecuteAction.class);
    return action == null || !action.isEnabled() ? null : action;
  }

  @Override
  protected void perform(@NotNull XDebugSession session, DataContext dataContext) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null || !(editor instanceof EditorEx)) {
      return;
    }

    int selectionStart = editor.getSelectionModel().getSelectionStart();
    int selectionEnd = editor.getSelectionModel().getSelectionEnd();
    String text;
    TextRange range;
    if (selectionStart != selectionEnd) {
      range = new TextRange(selectionStart, selectionEnd);
      text = editor.getDocument().getText(range);
    }
    else {
      XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
      if (evaluator != null) {
        ExpressionInfo expressionInfo = evaluator.getExpressionInfoAtOffset(session.getProject(), editor.getDocument(), selectionStart, true);
        if (expressionInfo == null) {
          return;
        }

        // todo check - is it wrong in case of not-null expressionInfo.second - copied (to console history document) text (text range) could be not correct statement?
        range = expressionInfo.getTextRange();
        text = XDebuggerEvaluateActionHandler.getExpressionText(expressionInfo, editor.getDocument());
      }
      else {
        return;
      }
    }

    if (StringUtil.isEmptyOrSpaces(text)) {
      return;
    }

    ConsoleExecuteAction action = getConsoleExecuteAction(session);
    if (action != null) {
      action.execute(range, text, (EditorEx)editor);
    }
  }
}