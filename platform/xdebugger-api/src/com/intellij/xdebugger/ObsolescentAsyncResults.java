package com.intellij.xdebugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;

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
}