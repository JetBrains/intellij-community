package org.jetbrains.rpc;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.PairConsumer;

final class NestedCommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, MAIN_RESULT, ERROR_DETAILS>
  extends CommandCallbackWithResponseBase<SUCCESS_RESPONSE, AsyncResult<MAIN_RESULT>, RESULT, ERROR_DETAILS> {
  private final PairConsumer<RESULT, AsyncResult<MAIN_RESULT>> consumer;

  public NestedCommandCallbackWithResponse(AsyncResult<MAIN_RESULT> mainAsyncResult, String methodName, PairConsumer<RESULT, AsyncResult<MAIN_RESULT>> consumer) {
    super(mainAsyncResult, methodName, null);

    this.consumer = consumer;
  }

  @Override
  protected void onSuccess(RESULT result) {
    consumer.consume(result, callback);
  }
}