package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.Value;

import java.util.Map;

/**
 * A context in which watch expressions may be evaluated. Typically corresponds to stack frame
 * of suspended process, but may also be detached from any stack frame
 */
public interface EvaluateContext {
  /**
   * Evaluates an arbitrary {@code expression} in the particular context.
   * Previously loaded {@link org.jetbrains.debugger.values.ObjectValue}s can be addressed from the expression if listed in
   * additionalContext parameter.
   */
  @NotNull
  AsyncResult<Value> evaluate(@NotNull String expression, @Nullable Map<String, EvaluateContextAdditionalParameter> additionalContext);

  @NotNull
  AsyncResult<Value> evaluate(@NotNull String expression);
}