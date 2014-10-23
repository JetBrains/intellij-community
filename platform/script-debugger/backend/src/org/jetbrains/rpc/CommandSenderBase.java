package org.jetbrains.rpc;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Function;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jsonProtocol.Request;
import org.jetbrains.jsonProtocol.RequestWithResponse;

public abstract class CommandSenderBase<SUCCESS_RESPONSE, ERROR_DETAILS> implements CommandSender<ERROR_DETAILS> {
  protected abstract void send(@NotNull Request message, @NotNull AsyncResultCallback<SUCCESS_RESPONSE, ERROR_DETAILS> callback);

  @Override
  public final ActionCallback send(@NotNull Request message) {
    CommandCallbackWithoutResponse<SUCCESS_RESPONSE, ERROR_DETAILS> callback = new CommandCallbackWithoutResponse<SUCCESS_RESPONSE, ERROR_DETAILS>();
    send(message, callback);
    return callback.callback;
  }

  @Override
  public final <RESULT, TRANSFORMED_RESULT> AsyncResult<TRANSFORMED_RESULT> send(@NotNull RequestWithResponse message, @NotNull Function<RESULT, TRANSFORMED_RESULT> transform) {
    return send(message, transform, null);
  }

  @Override
  public final <RESULT, TRANSFORMED_RESULT> AsyncResult<TRANSFORMED_RESULT> send(@NotNull RequestWithResponse message,
                                                                                 @NotNull Function<RESULT, TRANSFORMED_RESULT> transform,
                                                                                 @Nullable ErrorConsumer<AsyncResult<TRANSFORMED_RESULT>, ERROR_DETAILS> errorConsumer) {
    CommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS> callback =
      new CommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS>(message.getMethodName(), transform, errorConsumer);
    send(message, callback);
    return callback.callback;
  }

  @Override
  public final <RESULT, TRANSFORMED_RESULT> void send(@NotNull AsyncResult<TRANSFORMED_RESULT> result,
                                                      @NotNull RequestWithResponse message,
                                                      @NotNull Function<RESULT, TRANSFORMED_RESULT> transform) {
    send(message, new CommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS>(result, message.getMethodName(), transform, null));
  }

  @Override
  public final <RESULT, TRANSFORMED_RESULT> AsyncResult<TRANSFORMED_RESULT> send(@NotNull RequestWithResponse message,
                                                                                 @NotNull PairConsumer<RESULT, AsyncResult<TRANSFORMED_RESULT>> consumer) {
    return send(new AsyncResult<TRANSFORMED_RESULT>(), message, consumer);
  }

  @Override
  public final <RESULT, TRANSFORMED_RESULT> AsyncResult<TRANSFORMED_RESULT> send(@NotNull AsyncResult<TRANSFORMED_RESULT> result,
                                                                                 @NotNull RequestWithResponse message,
                                                                                 @NotNull PairConsumer<RESULT, AsyncResult<TRANSFORMED_RESULT>> consumer) {
    send(message, new NestedCommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS>(result, message.getMethodName(), consumer, null));
    return result;
  }

  @Override
  public final <RESULT, TRANSFORMED_RESULT> AsyncResult<TRANSFORMED_RESULT> send(@NotNull AsyncResult<TRANSFORMED_RESULT> result,
                                                                                 @NotNull RequestWithResponse message,
                                                                                 @NotNull PairConsumer<RESULT, AsyncResult<TRANSFORMED_RESULT>> consumer,
                                                                                 @Nullable ErrorConsumer<AsyncResult<TRANSFORMED_RESULT>, ERROR_DETAILS> errorConsumer) {
    send(message, new NestedCommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS>(result, message.getMethodName(), consumer, errorConsumer));
    return result;
  }
}