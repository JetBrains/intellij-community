package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.ValueManager;

public abstract class SuspendContextBase<VALUE_MANAGER extends ValueManager> implements SuspendContext {
  protected ExceptionData exceptionData;
  protected final boolean explicitPaused;

  protected final VALUE_MANAGER valueManager;

  protected SuspendContextBase(VALUE_MANAGER manager, boolean explicitPaused) {
    this.explicitPaused = explicitPaused;
    valueManager = manager;
  }

  @NotNull
  @Override
  public SuspendState getState() {
    return exceptionData == null ? (explicitPaused ? SuspendState.PAUSED : SuspendState.NORMAL) : SuspendState.EXCEPTION;
  }

  @NotNull
  @Override
  public final VALUE_MANAGER getValueManager() {
    return valueManager;
  }

  @Nullable
  @Override
  public Script getScript() {
    CallFrame topFrame = getTopFrame();
    return topFrame == null ? null : valueManager.getVm().getScriptManager().getScript(topFrame);
  }
}