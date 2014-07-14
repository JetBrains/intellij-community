/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XDebuggerEvaluateActionHandler extends XDebuggerActionHandler {
  @Override
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    XStackFrame stackFrame = session.getCurrentStackFrame();
    final XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
    if (evaluator == null) {
      return;
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

    String selectedText = editor != null ? editor.getSelectionModel().getSelectedText() : null;
    if (selectedText != null) {
      selectedText = evaluator.formatTextForEvaluation(selectedText);
    }
    String text = selectedText;

    if (text == null && editor != null) {
      text = getExpressionText(evaluator, CommonDataKeys.PROJECT.getData(dataContext), editor);
    }

    if (text == null) {
      XValue value = XDebuggerTreeActionBase.getSelectedValue(dataContext);
      if (value != null) {
        text = value.getEvaluationExpression();
      }
    }

    Language language = null;
    if (stackFrame != null) {
      XSourcePosition position = stackFrame.getSourcePosition();
      if (position != null) {
        language = XDebuggerEditorBase.getFileTypeLanguage(position.getFile().getFileType());
      }
    }
    if (language == null) {
      VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
      if (file != null) {
        language = XDebuggerEditorBase.getFileTypeLanguage(file.getFileType());
      }
    }
    XExpression expression = new XExpressionImpl(StringUtil.notNullize(text), language, null);
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
