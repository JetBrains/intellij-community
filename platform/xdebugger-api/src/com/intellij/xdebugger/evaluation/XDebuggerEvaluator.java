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
package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
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