package com.intellij.openapi.vcs.changes;

public enum InvokeAfterUpdateMode {
  SILENT(false, true, false, true),
  BACKGROUND_CANCELLABLE(true, false, false, true),
  BACKGROUND_NOT_CANCELLABLE(false, false, false, true),
  SYNCHRONOUS_CANCELLABLE(true, false, true, true),
  SYNCHRONOUS_NOT_CANCELLABLE(false, false, true, true),
  SILENT_CALLBACK_POOLED(false, true, false, false);

  private final boolean myCancellable;
  private final boolean mySilently;
  private final boolean mySynchronous;
  private final boolean myCallbackOnAwt;

  InvokeAfterUpdateMode(final boolean cancellable, final boolean silently, final boolean synchronous, final boolean callbackOnAwt) {
    myCancellable = cancellable;
    mySilently = silently;
    mySynchronous = synchronous;
    myCallbackOnAwt = callbackOnAwt;
  }

  public boolean isCancellable() {
    return myCancellable;
  }

  public boolean isSilently() {
    return mySilently;
  }

  public boolean isSynchronous() {
    return mySynchronous;
  }

  public boolean isCallbackOnAwt() {
    return myCallbackOnAwt;
  }
}
