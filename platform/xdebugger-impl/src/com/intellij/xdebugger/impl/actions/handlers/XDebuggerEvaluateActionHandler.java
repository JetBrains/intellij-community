/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

/**
 * @author nik
 */
public class XDebuggerEvaluateActionHandler extends XDebuggerActionHandler {
  @Override
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    final XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    final XStackFrame stackFrame = session.getCurrentStackFrame();
    final XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
    if (evaluator == null) {
      return;
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

    EvaluationMode mode = EvaluationMode.EXPRESSION;
    String selectedText = editor != null ? editor.getSelectionModel().getSelectedText() : null;
    if (selectedText != null) {
      selectedText = evaluator.formatTextForEvaluation(selectedText);
      mode = evaluator.getEvaluationMode(selectedText,
                                         editor.getSelectionModel().getSelectionStart(),
                                         editor.getSelectionModel().getSelectionEnd(),
                                         CommonDataKeys.PSI_FILE.getData(dataContext));
    }
    Promise<String> expressionTextPromise = Promise.resolve(selectedText);

    if (selectedText == null && editor != null) {
      expressionTextPromise = getExpressionText(evaluator, CommonDataKeys.PROJECT.getData(dataContext), editor);
    }

    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);

    EvaluationMode finalMode = mode;
    XValue value = XDebuggerTreeActionBase.getSelectedValue(dataContext);
    expressionTextPromise.done(expressionText -> {
      if (expressionText == null && value != null) {
          value.calculateEvaluationExpression().done(
            expression -> AppUIUtil.invokeOnEdt(() -> showDialog(session, file, editorsProvider, stackFrame, evaluator, expression)));
      }
      else {
        AppUIUtil.invokeOnEdt(() -> showDialog(session, file, editorsProvider, stackFrame, evaluator,
                                               XExpressionImpl.fromText(expressionText, finalMode)));
      }
    });
  }

  private static void showDialog(@NotNull XDebugSession session,
                                 VirtualFile file,
                                 XDebuggerEditorsProvider editorsProvider,
                                 XStackFrame stackFrame,
                                 XDebuggerEvaluator evaluator,
                                 @Nullable XExpression expression) {
    if (expression == null) {
      expression = XExpressionImpl.EMPTY_EXPRESSION;
    }
    if (expression.getLanguage() == null) {
      Language language = null;
      if (stackFrame != null) {
        XSourcePosition position = stackFrame.getSourcePosition();
        if (position != null) {
          language = LanguageUtil.getFileLanguage(position.getFile());
        }
      }
      if (language == null && file != null) {
        language = LanguageUtil.getFileTypeLanguage(file.getFileType());
      }
      expression = new XExpressionImpl(expression.getExpression(), language, expression.getCustomInfo(), expression.getMode());
    }
    XSourcePosition position = stackFrame == null ? null : stackFrame.getSourcePosition();
    new XDebuggerEvaluationDialog(session, editorsProvider, expression, position, evaluator.isCodeFragmentEvaluationSupported()).show();
  }

  /**
   * The value of resulting Promise can be null
   */
  @NotNull
  public static Promise<String> getExpressionText(@Nullable XDebuggerEvaluator evaluator, @Nullable Project project, @NotNull Editor editor) {
    if (project == null || evaluator == null) {
      return Promise.resolve(null);
    }

    Document document = editor.getDocument();
    Promise<ExpressionInfo> expressionInfoPromise = evaluator.getExpressionInfoAtOffsetAsync(project, document, editor.getCaretModel().getOffset(), true);
    return expressionInfoPromise.then(expressionInfo -> getExpressionText(expressionInfo, document));
  }

  @Nullable
  public static String getExpressionText(@Nullable ExpressionInfo expressionInfo, @NotNull Document document) {
    if (expressionInfo == null) {
      return null;
    }
    String text = expressionInfo.getExpressionText();
    return text == null ? document.getText(expressionInfo.getTextRange()) : text;
  }

  @Nullable
  public static String getDisplayText(@Nullable ExpressionInfo expressionInfo, @NotNull Document document) {
    if (expressionInfo == null) {
      return null;
    }
    String text = expressionInfo.getDisplayText();
    return text == null ? document.getText(expressionInfo.getTextRange()) : text;
  }

  @Override
  protected boolean isEnabled(final @NotNull XDebugSession session, final DataContext dataContext) {
    return session.getDebugProcess().getEvaluator() != null;
  }
}
