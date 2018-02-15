// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency;

import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public interface Promise<T> {
  enum State {
    PENDING, FULFILLED, REJECTED
  }

  @NotNull
  @Deprecated
  static RuntimeException createError(@NotNull String error) {
    return Promises.createError(error);
  }

  @NotNull
  static <T> Promise<T> resolve(T result) {
    return result == null ? Promises.resolvedPromise() : new DonePromise<>(result);
  }

  @NotNull
  Promise<T> done(@NotNull Consumer<? super T> done);

  @NotNull
  Promise<T> processed(@NotNull AsyncPromise<? super T> fulfilled);

  @NotNull
  Promise<T> rejected(@NotNull Consumer<Throwable> rejected);

  Promise<T> processed(@NotNull Consumer<? super T> processed);

  @NotNull
  <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<? super T, ? extends SUB_RESULT> done);

  @NotNull
  <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull Function<? super T, Promise<SUB_RESULT>> done);

  @NotNull
  State getState();

  @Nullable
  T blockingGet(int timeout, @NotNull TimeUnit timeUnit);

  default T blockingGet(int timeout) {
    return blockingGet(timeout, TimeUnit.MILLISECONDS);
  }

  void notify(@NotNull AsyncPromise<? super T> child);
}