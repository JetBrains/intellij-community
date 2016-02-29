/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
    String text = selectedText;

    if (text == null && editor != null) {
      text = getExpressionText(evaluator, CommonDataKeys.PROJECT.getData(dataContext), editor);
    }

    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);

    if (text == null) {
      XValue value = XDebuggerTreeActionBase.getSelectedValue(dataContext);
      if (value != null) {
        value.calculateEvaluationExpression()
          .done(expression -> {
          if (expression != null) {
            AppUIUtil.invokeOnEdt(() -> showDialog(session, file, editorsProvider, stackFrame, evaluator, expression));
          }
        });
        return;
      }
    }

    XExpression expression = XExpressionImpl.fromText(StringUtil.notNullize(text), mode);
    showDialog(session, file, editorsProvider, stackFrame, evaluator, expression);
  }

  private static void showDialog(@NotNull XDebugSession session,
                                 VirtualFile file,
                                 XDebuggerEditorsProvider editorsProvider,
                                 XStackFrame stackFrame,
                                 XDebuggerEvaluator evaluator,
                                 @NotNull XExpression expression) {
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
    new XDebuggerEvaluationDialog(session, editorsProvider, evaluator, expression, stackFrame == null ? null : stackFrame.getSourcePosition()).show();
  }

  @Nullable
  public static String getExpressionText(@Nullable XDebuggerEvaluator evaluator, @Nullable Project project, @NotNull Editor editor) {
    if (project == null || evaluator == null) {
      return null;
    }

    Document document = editor.getDocument();
    return getExpressionText(evaluator.getExpressionInfoAtOffset(project, document, editor.getCaretModel().getOffset(), true), document);
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
