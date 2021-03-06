// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Semaphore;
import java.util.concurrent.*;

public class FutureResult<T> implements Future<T> {
  private final Semaphore mySema = new Semaphore(0);
  private volatile Pair<Object, Boolean> myValue;

  public FutureResult() {
  }

  public FutureResult(@Nullable T result) {
    set(result);
  }

  public synchronized void set(@Nullable T result) {
    assertNotSet();

    myValue = Pair.create(result, true);
    mySema.release();
  }

  public synchronized void setException(@NotNull Throwable e) {
    assertNotSet();

    myValue = Pair.create(e, false);
    mySema.release();
  }

  public synchronized void reset() {
    try {
      // wait till readers get their results
      if (isDone()) mySema.acquire();
    }
    catch (InterruptedException ignore) {
      return;
    }
    myValue = null;
  }

  private void assertNotSet() {
    if (isDone()) throw new IllegalStateException("Result is already set");
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
    return myValue != null;
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    mySema.acquire();
    try {
      return doGet();
    }
    finally {
      mySema.release();
    }
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    if (!mySema.tryAcquire(timeout, unit)) throw new TimeoutException();
    try {
      return doGet();
    }
    finally {
      mySema.release();
    }
  }

  @Nullable
  public T tryGet() throws ExecutionException {
    return doGet();
  }

  @Nullable
  private T doGet() throws ExecutionException {
    Pair<Object, Boolean> pair = myValue;
    if (pair == null) return null;

    if (!pair.second) throw new ExecutionException(((Throwable)pair.first).getMessage(), (Throwable)pair.first);
    //noinspection unchecked
    return (T)pair.first;
  }
}
