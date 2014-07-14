package org.jetbrains.rpc;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.Nullable;

final class NestedCommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, MAIN_RESULT, ERROR_DETAILS>
  extends CommandCallbackWithResponseBase<SUCCESS_RESPONSE, AsyncResult<MAIN_RESULT>, RESULT, ERROR_DETAILS> {
  private final PairConsumer<RESULT, AsyncResult<MAIN_RESULT>> consumer;

  public NestedCommandCallbackWithResponse(AsyncResult<MAIN_RESULT> result, String methodName, PairConsumer<RESULT, AsyncResult<MAIN_RESULT>> consumer, @Nullable ErrorConsumer<AsyncResult<MAIN_RESULT>, ERROR_DETAILS> errorConsumer) {
    super(result, methodName, errorConsumer);

    this.consumer = consumer;
  }

  @Override
  protected void onSuccess(RESULT result) {
    consumer.consume(result, callback);
  }
}