package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An object that matches the execution state of the VM while suspended
 */
public interface SuspendContext {
  @NotNull
  SuspendState getState();

  @Nullable("if no frames (paused by user)")
  Script getScript();

  /**
   * @return the current exception state, or {@code null} if current state is
   *         not {@code EXCEPTION}
   * @see #getState()
   */
  @Nullable
  ExceptionData getExceptionData();

  @Nullable
  CallFrame getTopFrame();

  /**
   * @return a list of call frames for the current suspended state (from the
   * innermost (top) frame to the main (bottom) frame)
   */
  @NotNull
  AsyncResult<CallFrame[]> getCallFrames();

  /**
   * @return a set of the breakpoints hit on VM suspension with which this
   *         context is associated. An empty collection if the suspension was
   *         not related to hitting breakpoints (e.g. a step end)
   */
  @NotNull
  List<Breakpoint> getBreakpointsHit();

  /**
   * @return evaluate context for evaluating expressions in global scope
   */
  @Nullable
  EvaluateContext getGlobalEvaluateContext();

  /**
   * @return value mapping that all values have by default; typically unique for a particular {@link SuspendContext}
   */
  @NotNull
  ValueLoader<?> getValueLoader();
}
