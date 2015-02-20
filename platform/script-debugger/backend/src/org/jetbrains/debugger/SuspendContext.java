package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.values.ValueManager;

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
   * Call frames for the current suspended state (from the innermost (top) frame to the main (bottom) frame)
   */
  @NotNull
  Promise<CallFrame[]> getFrames();

  /**
   * list of the breakpoints hit on VM suspension with which this
   * context is associated. An empty collection if the suspension was
   * not related to hitting breakpoints (e.g. a step end)
   */
  @NotNull
  List<Breakpoint> getBreakpointsHit();

  @NotNull
  ValueManager getValueManager();
}
