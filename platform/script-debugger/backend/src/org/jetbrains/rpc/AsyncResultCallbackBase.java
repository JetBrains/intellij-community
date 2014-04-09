package org.jetbrains.rpc;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.Nullable;

abstract class AsyncResultCallbackBase<SUCCESS_RESPONSE, C extends ActionCallback, ERROR_DETAILS> implements AsyncResultCallback<SUCCESS_RESPONSE, ERROR_DETAILS> {
  protected final C callback;
  private final ErrorConsumer<C, ERROR_DETAILS> errorConsumer;

  protected AsyncResultCallbackBase(C callback, @Nullable ErrorConsumer<C, ERROR_DETAILS> errorConsumer) {
    this.callback = callback;
    this.errorConsumer = errorConsumer;
  }

  @Override
  public final void onError(String errorMessage, ERROR_DETAILS errorDetails) {
    try {
      if (errorConsumer == null) {
        callback.reject(errorMessage);
      }
      else {
        try {
          errorConsumer.consume(errorMessage, errorDetails, callback);
        }
        catch (Throwable e) {
          try {
            callback.reject(e.getMessage());
          }
          finally {
            MessageManager.LOG.error(e);
          }
        }
      }
    }
    catch (Throwable e) {
      MessageManager.LOG.error(e);
    }
  }
}