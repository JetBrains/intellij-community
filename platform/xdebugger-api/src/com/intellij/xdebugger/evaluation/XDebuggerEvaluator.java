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
package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XDebuggerEvaluator {

  /**
   * Evaluate <code>expression</code> to boolean
   *
   * @param expression expression to evaluate
   * @return result
   * @see XBreakpoint#getCondition()
   * @deprecated This method is used to evaluate breakpoints' conditions only. Instead of implementing it you should evaluate breakpoint's condition
   *             in your code and call {@link XDebugSession#breakpointReached(XBreakpoint, XSuspendContext)}
   *             only if the condition evaluates to <code>true</code>.
   */
  @Deprecated
  public boolean evaluateCondition(@NotNull String expression) {
    return true;
  }

  /**
   * Evaluate <code>expression</code> to string
   *
   * @param expression expression to evaluate
   * @return result
   * @deprecated This method is used to evaluate breakpoints' log messages only. Instead of implementing it you should evaluate breakpoint's
   *             log message in your code and pass it to {@link XDebugSession#breakpointReached(XBreakpoint, String, XSuspendContext)}.
   */
  @Deprecated
  @Nullable
  public String evaluateMessage(@NotNull String expression) {
    return null;
  }

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
     * called from evaluation dialog
     * @param expression expression to evaluate
     * @param callback   used to notify that the expression has been evaluated or an error occurs
     * @param mode       code fragment or expression
     */
  public void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition, @NotNull EvaluationMode mode) {
    evaluate(expression, callback, expressionPosition);
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
   * Return text range of expression which can be evaluated.
   *
   * @param project            project
   * @param document           document
   * @param offset             offset
   * @param sideEffectsAllowed if this parameter is false, the expression should not have any side effects when evaluated
   *                           (such expressions are evaluated in quick popups)
   * @return pair of text range of expression (to display as link) and actual expression to evaluate (optional, could be null)
   */
  @Nullable
  public Pair<TextRange, String> getExpressionAtOffset(@NotNull Project project, @NotNull Document document, int offset, boolean sideEffectsAllowed) {
    TextRange range = getExpressionRangeAtOffset(project, document, offset, sideEffectsAllowed);
    if (range == null) {
      return null;
    }
    else {
      return Pair.create(range, null);
    }
  }

  /**
   * Override this method to format selected text before it is shown in 'Evaluate' dialog
   */
  @NotNull
  public String formatTextForEvaluation(@NotNull String text) {
    return text;
  }

  /**
   * @return delay before showing value tooltip (in ms)
   */
  public int getValuePopupDelay() {
    return 700;
  }

  public interface XEvaluationCallback extends XValueCallback {
    void evaluated(@NotNull XValue result);
  }
}