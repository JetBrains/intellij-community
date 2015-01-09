package org.jetbrains.rpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

public abstract class MessageManagerBase {
  protected volatile boolean closed;

  protected final boolean rejectIfClosed(RequestCallback<?> callback) {
    if (closed) {
      callback.onError(Promise.createError("Connection closed"));
      return true;
    }
    return false;
  }

  public final void closed() {
    closed = true;
  }

  protected static void rejectCallback(@NotNull RequestCallback<?> callback) {
    callback.onError(Promise.createError("Connection closed"));
  }
}