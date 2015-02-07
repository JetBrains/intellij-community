package org.jetbrains.concurrency;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CountDownConsumer<T> implements Consumer<T> {
  private volatile int countDown;
  private final AsyncPromise<T> promise;
  private final T totalResult;

  public CountDownConsumer(int countDown, @NotNull AsyncPromise<T> promise, @Nullable T totalResult) {
    this.countDown = countDown;
    this.promise = promise;
    this.totalResult = totalResult;
  }

  @Override
  public void consume(T t) {
    if (--countDown == 0) {
      promise.setResult(totalResult);
    }
  }
}