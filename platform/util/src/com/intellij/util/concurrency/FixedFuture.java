// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** @deprecated use {@link java.util.concurrent.CompletableFuture#completedFuture} */
@Deprecated
@ApiStatus.ScheduledForRemoval
public class FixedFuture<T> implements Future<T> {
  private final T myValue;
  private final Throwable myThrowable;

  public FixedFuture(T value) {
    myValue = value;
    myThrowable = null;
  }

  private FixedFuture(@NotNull Throwable throwable) {
    myValue = null;
    myThrowable = throwable;
  }

  public static <T> FixedFuture<T> completeExceptionally(@NotNull Throwable throwable) {
    return new FixedFuture<>(throwable);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return true;
  }

  @Override
  public T get() throws ExecutionException {
    if (myThrowable != null) {
      throw new ExecutionException(myThrowable);
    }
    return myValue;
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit) {
    return myValue;
  }
}