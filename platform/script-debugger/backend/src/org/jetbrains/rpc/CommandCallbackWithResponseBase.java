package org.jetbrains.rpc;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.Nullable;

abstract class CommandCallbackWithResponseBase<SUCCESS_RESPONSE, C extends ActionCallback, RESULT, ERROR_DETAILS>
  extends AsyncResultCallbackBase<SUCCESS_RESPONSE, C, ERROR_DETAILS> {
  private final String methodName;

  public CommandCallbackWithResponseBase(C callback, String methodName, @Nullable ErrorConsumer<C, ERROR_DETAILS> errorConsumer) {
    super(callback, errorConsumer);
    
    this.methodName = methodName;

  }

  @Override
  public final void onSuccess(SUCCESS_RESPONSE response, ResultReader<SUCCESS_RESPONSE> resultReader) {
    try {
      onSuccess(resultReader.<RESULT>readResult(methodName, response));
    }
    catch (Throwable e) {
      callback.reject(e.getMessage());
      MessageManager.LOG.error(e);
    }
  }

  protected abstract void onSuccess(RESULT result);
}