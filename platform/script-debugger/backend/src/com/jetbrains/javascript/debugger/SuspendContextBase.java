package com.jetbrains.javascript.debugger;

public abstract class SuspendContextBase implements SuspendContext {
  protected ExceptionData exceptionData;
  protected final boolean explicitPaused;

  protected SuspendContextBase(boolean explicitPaused) {
    this.explicitPaused = explicitPaused;
  }

  @Override
  public SuspendState getState() {
    return exceptionData == null ? (explicitPaused ? SuspendState.PAUSED : SuspendState.NORMAL) : SuspendState.EXCEPTION;
  }
}