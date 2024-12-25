// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @deprecated use {@link java.util.concurrent.CompletableFuture} or {@link org.jetbrains.concurrency.Promise} */
@Deprecated
@SuppressWarnings({"DeprecatedIsStillUsed", "LambdaUnfriendlyMethodOverload", "BoundedWildcard"})
public class AsyncResult<T> extends ActionCallback {
  protected T myResult;

  public AsyncResult() { }

  public @NotNull AsyncResult<T> setDone(T result) {
    myResult = result;
    setDone();
    return this;
  }

  public @NotNull AsyncResult<T> setRejected(T result) {
    myResult = result;
    setRejected();
    return this;
  }

  public @NotNull AsyncResult<T> doWhenDone(final @NotNull Handler<? super T> handler) {
    doWhenDone(() -> handler.run(myResult));
    return this;
  }

  public @NotNull AsyncResult<T> doWhenDone(final @NotNull Consumer<? super T> consumer) {
    doWhenDone(() -> consumer.consume(myResult));
    return this;
  }

  @Override
  public final @NotNull AsyncResult<T> notify(final @NotNull ActionCallback child) {
    super.notify(child);
    return this;
  }

  public T getResult() {
    return myResult;
  }

  public T getResultSync() {
    return getResultSync(-1);
  }

  public @Nullable T getResultSync(long msTimeout) {
    waitFor(msTimeout);
    return myResult;
  }

  public interface Handler<T> {
    void run(T t);
  }

  public static @NotNull <R> AsyncResult<R> done(@Nullable R result) {
    return new AsyncResult<R>().setDone(result);
  }
}