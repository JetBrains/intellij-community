// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public abstract class XDebuggerEvaluator {
  /**
   * Start evaluating expression.
   *
   * @param expression expression to evaluate
   * @param callback   used to notify that the expression has been evaluated or an error occurs
   */
  public abstract void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition);

  /**
   * Start evaluating expression.
   *
   * @param expression         expression to evaluate
   * @param callback           used to notify that the expression has been evaluated or an error occurs
   * @param expressionPosition position where this expression should be evaluated
   */
  public void evaluate(@NotNull XExpression expression, @NotNull XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {
    evaluate(expression.getExpression(), callback, expressionPosition);
  }

  /**
   * If this method returns {@code true} 'Code Fragment Mode' button will be shown in 'Evaluate' dialog allowing user to execute a set of
   * statements
   *
   * @return {@code true} if debugger supports evaluation of code fragments (statements)
   */
  public boolean isCodeFragmentEvaluationSupported() {
    return true;
  }

  /**
   * Return text range of expression which can be evaluated.
   *
   * @param project            project
   * @param document           document
   * @param offset             offset
   * @param sideEffectsAllowed if this parameter is false, the expression should not have any side effects when evaluated
   *                           (such expressions are evaluated in quick popups)
   * @return text range of expression
   */
  @Nullable
  public TextRange getExpressionRangeAtOffset(final Project project, final Document document, int offset, boolean sideEffectsAllowed) {
    return null;
  }

  /**
   * @param project            project
   * @param document           document
   * @param offset             offset
   * @param sideEffectsAllowed if this parameter is false, the expression should not have any side effects when evaluated
   *                           (such expressions are evaluated in quick popups)
   * @return {@link ExpressionInfo} of expression which can be evaluated
   */
  @Nullable
  public ExpressionInfo getExpressionInfoAtOffset(@NotNull Project project, @NotNull Document document, int offset, boolean sideEffectsAllowed) {
    TextRange range = getExpressionRangeAtOffset(project, document, offset, sideEffectsAllowed);
    return range == null ? null : new ExpressionInfo(range);
  }

  /**
   * Async version of {@link #getExpressionInfoAtOffset(Project, Document, int, boolean)}. Overload this method if you cannot evaluate ExpressionInfo in sync way.
   * The value of the resulting Promise can be null
   */
  @NotNull
  public Promise<ExpressionInfo> getExpressionInfoAtOffsetAsync(@NotNull Project project, @NotNull Document document, int offset, boolean sideEffectsAllowed) {
    return Promises.resolvedPromise(getExpressionInfoAtOffset(project, document, offset, sideEffectsAllowed));
  }
  /**
   * Override this method to format selected text before it is shown in 'Evaluate' dialog
   */
  @NotNull
  public String formatTextForEvaluation(@NotNull String text) {
    return text;
  }

  /**
   * Returns mode which should be used to evaluate the text
   */
  public EvaluationMode getEvaluationMode(@NotNull String text, int startOffset, int endOffset, @Nullable PsiFile psiFile) {
    return text.contains("\n") ? EvaluationMode.CODE_FRAGMENT : EvaluationMode.EXPRESSION;
  }

  public interface XEvaluationCallback extends XValueCallback {
    void evaluated(@NotNull XValue result);
  }
}