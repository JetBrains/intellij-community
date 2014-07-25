package org.jetbrains.rpc;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

final class CommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS>
  extends CommandCallbackWithResponseBase<SUCCESS_RESPONSE, AsyncResult<TRANSFORMED_RESULT>, RESULT, ERROR_DETAILS> {
  private final Function<RESULT, TRANSFORMED_RESULT> transform;

  public CommandCallbackWithResponse(String methodName, Function<RESULT, TRANSFORMED_RESULT> transform, @Nullable ErrorConsumer<AsyncResult<TRANSFORMED_RESULT>, ERROR_DETAILS> errorConsumer) {
    this(new AsyncResult<TRANSFORMED_RESULT>(), methodName, transform, errorConsumer);
  }

  public CommandCallbackWithResponse(AsyncResult<TRANSFORMED_RESULT> asyncResult, String methodName, Function<RESULT, TRANSFORMED_RESULT> transform, @Nullable ErrorConsumer<AsyncResult<TRANSFORMED_RESULT>, ERROR_DETAILS> errorConsumer) {
    super(asyncResult, methodName, errorConsumer);

    this.transform = transform;
  }

  @Override
  protected void onSuccess(RESULT result) {
    try {
      callback.setDone(transform.fun(result));
    }
    catch (Throwable e) {
      MessageManager.LOG.error(e);
    }
  }
}