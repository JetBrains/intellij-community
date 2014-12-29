package org.jetbrains.concurrency;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

class CountDownConsumer implements Consumer<Void> {
  private volatile int countDown;
  private final AsyncPromise<Void> promise;

  public CountDownConsumer(int countDown, @NotNull AsyncPromise<Void> promise) {
    this.countDown = countDown;
    this.promise = promise;
  }

  @Override
  public void consume(Void t) {
    if (--countDown == 0) {
      promise.setResult(null);
    }
  }
}