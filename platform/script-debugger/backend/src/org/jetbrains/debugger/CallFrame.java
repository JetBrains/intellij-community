package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CallFrame {
  /**
   * @return the scopes known in this frame
   */
  @NotNull
  List<Scope> getVariableScopes();

  /**
   * @return the receiver variable known in this frame ("this" variable)
   *
   * Computed variable must be null if no receiver variable, call {@link com.intellij.openapi.util.AsyncResult#setDone(Object null)}
   */
  @NotNull
  AsyncResult<Variable> getReceiverVariable();

  int getLine();

  int getColumn();

  /**
   * @return the name of the current function of this frame
   */
  @Nullable
  String getFunctionName();

  /**
   * @return context for evaluating expressions in scope of this frame
   */
  @NotNull
  EvaluateContext getEvaluateContext();

  /**
   @see com.intellij.xdebugger.frame.XStackFrame#getEqualityObject()
   */
  Object getEqualityObject();
}