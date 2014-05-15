package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
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

  /**
   * optional to implement, some protocols, WIP for example, require you to release remote objects
   */
  @NotNull
  EvaluateContext withValueManager(@NotNull String objectGroup);

  /**
   * If you evaluate "foo.bar = 4" and want to update Variables view (and all other clients), you can use use this task
   */
  @NotNull
  ActionCallback refreshOnDone(@NotNull ActionCallback result);

  /**
   * call only if withLoader was called before
   */
  void releaseObjects();
}