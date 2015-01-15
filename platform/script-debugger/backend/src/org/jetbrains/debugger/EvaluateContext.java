package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

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
  Promise<EvaluateResult> evaluate(@NotNull String expression, @Nullable Map<String, Object> additionalContext, boolean enableBreak);

  @NotNull
  Promise<EvaluateResult> evaluate(@NotNull String expression);

  @NotNull
  Promise<EvaluateResult> evaluate(@NotNull String expression, boolean enableBreak);

  /**
   * optional to implement, some protocols, WIP for example, require you to release remote objects
   */
  @NotNull
  EvaluateContext withValueManager(@NotNull String objectGroup);

  /**
   * If you evaluate "foo.bar = 4" and want to update Variables view (and all other clients), you can use use this task
   * @param promise
   */
  @NotNull
  Promise<?> refreshOnDone(@NotNull Promise<?> promise);

  /**
   * call only if withLoader was called before
   */
  void releaseObjects();
}