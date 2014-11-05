package org.jetbrains.rpc;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Function;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.jsonProtocol.Request;
import org.jetbrains.jsonProtocol.RequestWithResponse;

public abstract class CommandSenderBase<SUCCESS_RESPONSE, ERROR_DETAILS> implements CommandSender<ERROR_DETAILS> {
  protected abstract void send(@NotNull Request message, @NotNull AsyncResultCallback<SUCCESS_RESPONSE, ERROR_DETAILS> callback);

  @NotNull
  @Override
  public final Promise<Void> send(@NotNull Request message) {
    PromiseWrapper<SUCCESS_RESPONSE, Void, ERROR_DETAILS> callback = new PromiseWrapper<SUCCESS_RESPONSE, Void, ERROR_DETAILS>(null);
    send(message, callback);
    return callback;
  }

  @Override
  public final <RESULT> Promise<RESULT> send(@NotNull RequestWithResponse<RESULT> request) {
    PromiseWrapper<SUCCESS_RESPONSE, RESULT, ERROR_DETAILS> callback = new PromiseWrapper<SUCCESS_RESPONSE, RESULT, ERROR_DETAILS>(request.getMethodName());
    send(request, callback);
    return callback;
  }

  private static final class PromiseWrapper<SUCCESS_RESPONSE, RESULT, ERROR_DETAILS> extends AsyncPromise<RESULT> implements AsyncResultCallback<SUCCESS_RESPONSE, ERROR_DETAILS> {
    private final String methodName;

    public PromiseWrapper(@Nullable String methodName) {
      this.methodName = methodName;
    }

    @Override
    public void onSuccess(@Nullable SUCCESS_RESPONSE response, @NotNull ResultReader<SUCCESS_RESPONSE> resultReader) {
      try {
        setResult(methodName == null ? null : resultReader.<RESULT>readResult(methodName, response));
      }
      catch (Throwable e) {
        CommandProcessor.LOG.error(e);
        setError(e.getMessage());
      }
    }

    @Override
    public void onError(@NotNull String errorMessage, ERROR_DETAILS details) {
      setError(errorMessage);
    }
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