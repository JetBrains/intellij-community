package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.xdebugger.Obsolescent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

public final class ObsolescentAsyncResults {
  @NotNull
  public static <T> AsyncResult<T> consume(@NotNull final AsyncResult<T> result,
                                           @NotNull final Obsolescent obsolescent,
                                           @NotNull final Consumer<T> consumer) {
    result.doWhenDone(new Runnable() {
      @Override
      public void run() {
        if (!obsolescent.isObsolete()) {
          consumer.consume(result.getResult());
        }
      }
    });
    return result;
  }

  @NotNull
  public static <O extends Obsolescent, T> AsyncResult<T> consume(@NotNull final AsyncResult<T> result,
                                                                  @NotNull final O obsolescent,
                                                                  @NotNull final PairConsumer<T, O> consumer) {
    result.doWhenDone(new Runnable() {
      @Override
      public void run() {
        if (!obsolescent.isObsolete()) {
          consumer.consume(result.getResult(), obsolescent);
        }
      }
    });
    return result;
  }

  @NotNull
  public static <O extends Obsolescent, T> Promise<T> consume(@NotNull final Promise<T> promise,
                                                        @NotNull final O obsolescent,
                                                        @NotNull final PairConsumer<T, O> consumer) {
    promise.done(new Consumer<T>() {
      @Override
      public void consume(T result) {
        if (!obsolescent.isObsolete()) {
          consumer.consume(result, obsolescent);
        }
      }
    });
    return promise;
  }
}