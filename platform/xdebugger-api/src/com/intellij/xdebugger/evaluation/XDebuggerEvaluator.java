package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.frame.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XDebuggerEvaluator {

  /**
   * Evaluate <code>expression</code> to boolean
   * @param expression expression to evaluate
   * @return result
   *
   * @deprecated This method is used to evaluate breakpoints' conditions only. Instead of implementing it you should evaluate breakpoint's condition
   * in your code and call {@link XDebugSession#breakpointReached(XBreakpoint, XSuspendContext)}
   * only if the condition evaluates to <code>true</code>.
   *
   * @see XBreakpoint#getCondition()
   */
  @Deprecated
  public boolean evaluateCondition(@NotNull String expression) {
    return true;
  }

  /**
   * Evaluate <code>expression</code> to string
   * @param expression expression to evaluate
   * @return result
   *
   * @deprecated This method is used to evaluate breakpoints' log messages only. Instead of implementing it you should evaluate breakpoint's
   * log message in your code and pass it to {@link XDebugSession#breakpointReached(XBreakpoint, String, XSuspendContext)}.
   */
  @Deprecated
  @Nullable
  public String evaluateMessage(@NotNull String expression) {
    return null;
  }

  /**
   * Start evaluating expression.
   * @param expression expression to evaluate
   * @param callback used to notify that the expression has been evaluated or an error occurs
   */
  public abstract void evaluate(@NotNull String expression, XEvaluationCallback callback);

  /**
   * Return text range of expression which can be evaluated.
   * @param project project
   * @param document document
   * @param offset offset
   * @return text range of expression
   */
  @Nullable
  public abstract TextRange getExpressionRangeAtOffset(final Project project, final Document document, int offset);

  /**
   * @return delay before showing value tooltip (in ms)
   */
  public int getValuePopupDelay() {
    return 700;
  }

  public interface XEvaluationCallback {

    void evaluated(@NotNull XValue result);

    void errorOccurred(@NotNull String errorMessage);

    /**
     * @deprecated use {@link XEvaluationCallback#errorOccurred(String)}
     */
    @Deprecated
    void errorOccured(@NotNull String errorMessage);

  }
}
