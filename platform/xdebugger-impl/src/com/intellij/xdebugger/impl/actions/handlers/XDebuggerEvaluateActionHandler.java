// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ComponentUtil;
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
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.awt.*;

@ApiStatus.Internal
public class XDebuggerEvaluateActionHandler extends XDebuggerActionHandler {
  @Override
  protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
    final XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    final XStackFrame stackFrame = session.getCurrentStackFrame();
    final XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
    if (evaluator == null) {
      return;
    }
    DataContext focusedDataContext = extractFocusedDataContext(dataContext);
    if (focusedDataContext != null) {
      dataContext = focusedDataContext;
    }

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    XValueNodeImpl node = XDebuggerTreeActionBase.getSelectedNode(dataContext);
    XValue selectedValue = node == null ? null : node.getValueContainer();

    getSelectedExpressionAsync(project, evaluator, editor, psiFile, selectedValue)
      .onSuccess(expression -> AppUIUtil.invokeOnEdt(() -> showDialog(session, file, editorsProvider, stackFrame, evaluator, expression)));
  }

  public static @Nullable DataContext extractFocusedDataContext(DataContext actionDataContext) {
    // replace data context, because we need to have it for the focused component, not the target component (if from the toolbar)
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
    if (focusOwner == null) {
      // maybe this is in the toolbar menu - use getMostRecentFocusOwner
      Window window = ComponentUtil.getWindow(actionDataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
      if (window != null) {
        focusOwner = window.getMostRecentFocusOwner();
      }
    }
    if (focusOwner != null && ClientId.isCurrentlyUnderLocalId()) {
      return DataManager.getInstance().getDataContext(focusOwner);
    }
    return null;
  }

  public static Promise<@Nullable XExpression> getSelectedExpressionAsync(
    @Nullable Project project,
    @NotNull XDebuggerEvaluator evaluator,
    @Nullable Editor editor,
    @Nullable PsiFile psiFile,
    @Nullable XValue selectedValue
  ) {
    return getSelectedTextAsync(project, evaluator, editor, psiFile)
      .thenAsync(pair -> {
        Promise<XExpression> expressionPromise = Promises.resolvedPromise(null);
        if (pair.first != null) {
          expressionPromise = Promises.resolvedPromise(XExpressionImpl.fromText(pair.first, pair.second));
        }
        else if (selectedValue != null) {
          expressionPromise = selectedValue.calculateEvaluationExpression();
        }
        return expressionPromise;
      });
  }

  public static Promise<Pair<@Nullable String, EvaluationMode>> getSelectedTextAsync(
    @Nullable Project project,
    @NotNull XDebuggerEvaluator evaluator,
    @Nullable Editor editor,
    @Nullable PsiFile psiFile
  ) {
    EvaluationMode mode = EvaluationMode.EXPRESSION;
    String selectedText = editor != null ? editor.getSelectionModel().getSelectedText() : null;
    if (selectedText != null) {
      selectedText = evaluator.formatTextForEvaluation(selectedText);
      mode = evaluator.getEvaluationMode(selectedText,
                                         editor.getSelectionModel().getSelectionStart(),
                                         editor.getSelectionModel().getSelectionEnd(),
                                         psiFile);
    }
    Promise<String> expressionTextPromise = Promises.resolvedPromise(selectedText);

    if (selectedText == null && editor != null) {
      expressionTextPromise = getExpressionText(evaluator, project, editor);
    }

    EvaluationMode finalMode = mode;
    return expressionTextPromise.then(expression -> Pair.create(expression, finalMode));
  }

  public static void showDialog(@NotNull XDebugSession session,
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
  public static @NotNull Promise<String> getExpressionText(@Nullable XDebuggerEvaluator evaluator, @Nullable Project project, @NotNull Editor editor) {
    if (project == null || evaluator == null) {
      return Promises.resolvedPromise(null);
    }

    Document document = editor.getDocument();
    Promise<ExpressionInfo> expressionInfoPromise = evaluator.getExpressionInfoAtOffsetAsync(project, document, editor.getCaretModel().getOffset(), true);
    return expressionInfoPromise.then(expressionInfo -> getExpressionText(expressionInfo, document));
  }

  public static @Nullable String getExpressionText(@Nullable ExpressionInfo expressionInfo, @NotNull Document document) {
    if (expressionInfo == null) {
      return null;
    }
    String text = expressionInfo.getExpressionText();
    return text == null ? document.getText(expressionInfo.getTextRange()) : text;
  }

  public static @Nullable String getDisplayText(@Nullable ExpressionInfo expressionInfo, @NotNull Document document) {
    if (expressionInfo == null) {
      return null;
    }
    String text = expressionInfo.getDisplayText();
    return text == null ? document.getText(expressionInfo.getTextRange()) : text;
  }

  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
    return session.getDebugProcess().getEvaluator() != null;
  }
}
