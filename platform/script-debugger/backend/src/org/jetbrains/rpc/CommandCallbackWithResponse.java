package org.jetbrains.rpc;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS>
  extends AsyncResultCallbackBase<SUCCESS_RESPONSE,AsyncResult<TRANSFORMED_RESULT>,ERROR_DETAILS> {
  private final Function<RESULT, TRANSFORMED_RESULT> transform;
  private final String methodName;

  public CommandCallbackWithResponse(String methodName, Function<RESULT, TRANSFORMED_RESULT> transform, @Nullable ErrorConsumer<AsyncResult<TRANSFORMED_RESULT>, ERROR_DETAILS> errorConsumer) {
    this(new AsyncResult<TRANSFORMED_RESULT>(), methodName, transform, errorConsumer);
  }

  public CommandCallbackWithResponse(AsyncResult<TRANSFORMED_RESULT> asyncResult, String methodName, Function<RESULT, TRANSFORMED_RESULT> transform, @Nullable ErrorConsumer<AsyncResult<TRANSFORMED_RESULT>, ERROR_DETAILS> errorConsumer) {
    super(asyncResult, errorConsumer);

    this.methodName = methodName;

    this.transform = transform;
  }

  private void onSuccess(RESULT result) {
    try {
      callback.setDone(transform.fun(result));
    }
    catch (Throwable e) {
      CommandProcessor.LOG.error(e);
    }
  }

  @Override
  public final void onSuccess(SUCCESS_RESPONSE response, @NotNull ResultReader<SUCCESS_RESPONSE> resultReader) {
    try {
      onSuccess(resultReader.<RESULT>readResult(methodName, response));
    }
    catch (Throwable e) {
      CommandProcessor.LOG.error(e);
      callback.reject(e.getMessage());
    }
  }
}