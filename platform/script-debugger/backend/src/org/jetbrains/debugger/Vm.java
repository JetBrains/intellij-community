package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public interface Vm {
  interface AttachStateManager {
    @NotNull
    Promise<Void> detach();

    boolean isAttached();
  }

  @NotNull
  AttachStateManager getAttachStateManager();

  @NotNull
  ScriptManager getScriptManager();

  @NotNull
  BreakpointManager getBreakpointManager();

  @NotNull
  SuspendContextManager getSuspendContextManager();

  /**
   * Controls whether VM stops on exceptions
   */
  @NotNull
  Promise<?> setBreakOnException(@NotNull ExceptionCatchMode catchMode);

  @Nullable("if global evaluate not supported")
  EvaluateContext getEvaluateContext();

  @NotNull
  DebugEventListener getDebugListener();
}