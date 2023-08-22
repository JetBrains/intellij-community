// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// future which is loud about exceptions during its execution
final class FlippantFuture<T> implements RunnableFuture<T> {

  private final @NotNull RunnableFuture<? extends T> myFuture;

  FlippantFuture(@NotNull RunnableFuture<? extends T> future) {
    myFuture = future;
  }

  @Override
  public void run() {
    myFuture.run();
    ConcurrencyUtil.manifestExceptionsIn(myFuture);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return myFuture.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return myFuture.isCancelled();
  }

  @Override
  public boolean isDone() {
    return myFuture.isDone();
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    return myFuture.get();
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return myFuture.get(timeout, unit);
  }
}
