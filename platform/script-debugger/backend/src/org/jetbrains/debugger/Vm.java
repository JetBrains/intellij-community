package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;

public interface Vm {
  @NotNull
  ActionCallback detach();

  boolean isAttached();

  @NotNull
  ScriptManager getScriptManager();

  @NotNull
  BreakpointManager getBreakpointManager();

  @NotNull
  SuspendContextManager getSuspendContextManager();

  /**
   * Asynchronously enables or disables all breakpoints on remote. 'Enabled' means that
   * breakpoints behave as normal, 'disabled' means that VM doesn't stop on breakpoints.
   * It doesn't update individual properties of {@link Breakpoint}s. Method call
   * with a null value and not null callback simply returns current value.
   * @param enabled new value to set or null
   */
  @NotNull
  ActionCallback enableBreakpoints(boolean enabled);

  /**
   * Controls whether VM stops on exceptions
   */
  @NotNull
  ActionCallback setBreakOnException(ExceptionCatchMode catchMode);

  @NotNull
  EvaluateContext getEvaluateContext();

  @NotNull
  DebugEventListener getDebugListener();
}