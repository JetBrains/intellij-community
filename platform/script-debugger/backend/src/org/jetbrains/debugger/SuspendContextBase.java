package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;

public abstract class SuspendContextBase implements SuspendContext {
  protected ExceptionData exceptionData;
  protected final boolean explicitPaused;

  protected SuspendContextBase(boolean explicitPaused) {
    this.explicitPaused = explicitPaused;
  }

  @NotNull
  @Override
  public SuspendState getState() {
    return exceptionData == null ? (explicitPaused ? SuspendState.PAUSED : SuspendState.NORMAL) : SuspendState.EXCEPTION;
  }
}