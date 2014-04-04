package org.jetbrains.rpc;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Function;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jsonProtocol.Request;
import org.jetbrains.jsonProtocol.RequestWithResponse;

public interface CommandSender<ERROR_DETAILS> {
  ActionCallback send(Request message);

  <RESULT, TRANSFORMED_RESULT> AsyncResult<TRANSFORMED_RESULT> send(RequestWithResponse message, Function<RESULT, TRANSFORMED_RESULT> transform);

  <RESULT, TRANSFORMED_RESULT> AsyncResult<TRANSFORMED_RESULT> send(RequestWithResponse message,
                                                                    Function<RESULT, TRANSFORMED_RESULT> transform,
                                                                    @Nullable ErrorConsumer<AsyncResult<TRANSFORMED_RESULT>, ERROR_DETAILS> errorConsumer);

  <RESULT, TRANSFORMED_RESULT> void send(AsyncResult<TRANSFORMED_RESULT> precreatedAsyncResult, RequestWithResponse message, Function<RESULT, TRANSFORMED_RESULT> transform);

  <RESULT, MAIN_RESULT> AsyncResult<MAIN_RESULT> sendNested(AsyncResult<MAIN_RESULT> mainAsyncResult, RequestWithResponse message, PairConsumer<RESULT, AsyncResult<MAIN_RESULT>> consumer);
}