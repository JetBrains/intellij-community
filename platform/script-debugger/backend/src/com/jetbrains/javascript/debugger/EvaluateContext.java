package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A context in which watch expressions may be evaluated. Typically corresponds to stack frame
 * of suspended process, but may also be detached from any stack frame
 */
public interface EvaluateContext {
  /**
   * Evaluates an arbitrary JavaScript {@code expression} in the particular context.
   * Previously loaded {@link ObjectValue}s can be addressed from the expression if listed in
   * additionalContext parameter.
   *
   * @param expression        to evaluate
   * @param additionalContext a name-to-value map that adds new values to an expression scope; may be null
   */
  @NotNull
  AsyncResult<Value> evaluate(@NotNull String expression, @Nullable Map<String, EvaluateContextAdditionalParameter> additionalContext);

  @NotNull
  AsyncResult<Value> evaluate(@NotNull String expression);
}