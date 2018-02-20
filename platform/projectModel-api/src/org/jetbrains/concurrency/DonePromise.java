// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency;

import com.intellij.openapi.util.Getter;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.jetbrains.concurrency.Promises.rejectedPromise;
import static org.jetbrains.concurrency.Promises.resolvedPromise;

class DonePromise<T> implements Getter<T>, Promise<T>, Future<T> {
  private final T result;

  public DonePromise(T result) {
    this.result = result;
  }

  @NotNull
  @Override
  public Promise<T> onSuccess(@NotNull Consumer<? super T> done) {
    if (!AsyncPromiseKt.isObsolete(done)) {
      done.accept(result);
    }
    return this;
  }

  @NotNull
  @Override
  public Promise<T> processed(@NotNull Promise<? super T> child) {
    if (child instanceof AsyncPromise) {
      //noinspection unchecked
      ((AsyncPromise<? super T>)child).setResult(result);
    }
    return this;
  }

  @NotNull
  @Override
  public Promise<T> onProcessed(@NotNull Consumer<? super T> processed) {
    onSuccess(processed);
    return this;
  }

  @NotNull
  @Override
  public Promise<T> onError(@NotNull Consumer<Throwable> rejected) {
    return this;
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<? super T, ? extends SUB_RESULT> done) {
    if (done instanceof Obsolescent && ((Obsolescent)done).isObsolete()) {
      return rejectedPromise("obsolete");
    }
    else {
      return resolvedPromise(done.fun(result));
    }
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull Function<? super T, Promise<SUB_RESULT>> done) {
    return done.fun(result);
  }

  @NotNull
  @Override
  public State getState() {
    return State.FULFILLED;
  }

  @Nullable
  @Override
  public T blockingGet(int timeout, @NotNull TimeUnit timeUnit) {
    return result;
  }

  @Override
  public boolean isDone() {
    return getState() != State.PENDING;
  }

  @Override
  public T get() {
    return result;
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
  public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return result;
  }
}