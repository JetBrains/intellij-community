package org.jetbrains.rpc;

import org.jetbrains.annotations.NotNull;

public abstract class MessageManagerBase {
  protected volatile boolean closed;

  protected final boolean rejectIfClosed(AsyncResultCallback<?, ?> callback) {
    if (closed) {
      callback.onError("Connection closed", null);
      return true;
    }
    return false;
  }

  public final void closed() {
    closed = true;
  }

  protected static void rejectCallback(@NotNull AsyncResultCallback<?, ?> callback) {
    callback.onError("Connection closed", null);
  }
}