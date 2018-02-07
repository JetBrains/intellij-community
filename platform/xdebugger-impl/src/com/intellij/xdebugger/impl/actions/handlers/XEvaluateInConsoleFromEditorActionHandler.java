package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

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
    if (!(editor instanceof EditorEx)) {
      return;
    }

    int selectionStart = editor.getSelectionModel().getSelectionStart();
    int selectionEnd = editor.getSelectionModel().getSelectionEnd();
    Promise<Pair<TextRange, String>> rangeAndText = null;
    if (selectionStart != selectionEnd) {
      TextRange textRange = new TextRange(selectionStart, selectionEnd);
      rangeAndText = Promise.resolve(Pair.create(textRange, editor.getDocument().getText(textRange)));
    } else {
      XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
      if (evaluator != null) {
        Promise<ExpressionInfo> expressionInfoPromise = evaluator.getExpressionInfoAtOffsetAsync(session.getProject(), editor.getDocument(), selectionStart, true);
        rangeAndText = expressionInfoPromise.then(expressionInfo -> {
          if (expressionInfo == null) {
            return null;
          }

          // todo check - is it wrong in case of not-null expressionInfo.second - copied (to console history document) text (text range) could be not correct statement?
          return Pair.create(expressionInfo.getTextRange(), XDebuggerEvaluateActionHandler.getExpressionText(expressionInfo, editor.getDocument()));
        });

      } else {
        return;
      }
    }

    rangeAndText.done(textRangeStringPair -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        TextRange range = textRangeStringPair.getFirst();
        String text = textRangeStringPair.getSecond();
        if (text == null)
          return;
        ConsoleExecuteAction action = getConsoleExecuteAction(session);
        if (action != null) {
          action.execute(range, text, (EditorEx) editor);
        }
      });
    });
  }
}