package org.jetbrains.rpc;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Function;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jsonProtocol.Request;
import org.jetbrains.jsonProtocol.RequestWithResponse;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class MessageHandler<INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE, ERROR_DETAILS>
  implements MessageManager.Handler<Request, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE, ERROR_DETAILS>, ResultReader<SUCCESS_RESPONSE>, CommandSender<ERROR_DETAILS> {
  private final AtomicInteger currentSequence = new AtomicInteger();
  protected final MessageManager<Request, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE, ERROR_DETAILS> messageManager;

  protected MessageHandler() {
    messageManager = new MessageManager<Request, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE, ERROR_DETAILS>(this);
  }

  public void cancelWaitingRequests() {
    messageManager.cancelWaitingRequests();
  }

  public void closed() {
    messageManager.closed();
  }

  @Override
  public final int getUpdatedSequence(Request message) {
    int id = currentSequence.incrementAndGet();
    message.finalize(id);
    return id;
  }

  @Override
  public final ActionCallback send(Request message) {
    CommandCallbackWithoutResponse<SUCCESS_RESPONSE, ERROR_DETAILS> callback = new CommandCallbackWithoutResponse<SUCCESS_RESPONSE, ERROR_DETAILS>();
    messageManager.send(message, callback);
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
    messageManager.send(message, callback);
    return callback.callback;
  }

  @Override
  public final <RESULT, TRANSFORMED_RESULT> void send(@NotNull AsyncResult<TRANSFORMED_RESULT> result,
                                                      @NotNull RequestWithResponse message,
                                                      @NotNull Function<RESULT, TRANSFORMED_RESULT> transform) {
    messageManager.send(message, new CommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS>(result, message.getMethodName(), transform, null));
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
    messageManager.send(message, new NestedCommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS>(result, message.getMethodName(), consumer, null));
    return result;
  }

  @Override
  public final <RESULT, TRANSFORMED_RESULT> AsyncResult<TRANSFORMED_RESULT> send(@NotNull AsyncResult<TRANSFORMED_RESULT> result,
                                                                                 @NotNull RequestWithResponse message,
                                                                                 @NotNull PairConsumer<RESULT, AsyncResult<TRANSFORMED_RESULT>> consumer,
                                                                                 @Nullable ErrorConsumer<AsyncResult<TRANSFORMED_RESULT>, ERROR_DETAILS> errorConsumer) {
    messageManager.send(message, new NestedCommandCallbackWithResponse<SUCCESS_RESPONSE, RESULT, TRANSFORMED_RESULT, ERROR_DETAILS>(result, message.getMethodName(), consumer, errorConsumer));
    return result;
  }
}