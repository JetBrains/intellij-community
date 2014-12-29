package org.jetbrains.concurrency;

import com.intellij.util.Consumer;
import com.intellij.util.Function;

public abstract class ConsumerRunnable implements Consumer<Void>, Runnable, Function<Object, Void> {
  @Override
  public final void consume(Void v) {
    run();
  }

  @Override
  public final Void fun(Object object) {
    run();
    return null;
  }
}