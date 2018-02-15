// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency;

import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * The Promise represents the eventual completion (or failure) of an asynchronous operation, and its resulting value.
 *
 * A Promise is a proxy for a value not necessarily known when the promise is created.
 * It allows you to associate handlers with an asynchronous action's eventual success value or failure reason.
 * This lets asynchronous methods return values like synchronous methods: instead of immediately returning the final value,
 * the asynchronous method returns a promise to supply the value at some point in the future.
 *
 * A Promise is in one of these states:
 *
 * <ul>
 *   <li>pending: initial state, neither fulfilled nor rejected.</li>
 *   <li>fulfilled: meaning that the operation completed successfully.</li>
 *   <li>rejected: meaning that the operation failed.</li>
 * </ul>
 */
public interface Promise<T> {
  enum State {
    PENDING, FULFILLED, REJECTED
  }

  @NotNull
  static <T> Promise<T> resolve(T result) {
    return Promises.resolvedPromise(result);
  }

  @NotNull
  Promise<T> done(@NotNull Consumer<? super T> done);

  /**
   * Resolve or reject passed promise as soon as this promise resolved or rejected.
   */
  @NotNull
  Promise<T> processed(@NotNull Promise<? super T> child);

  @NotNull
  Promise<T> rejected(@NotNull Consumer<Throwable> rejected);

  /**
   * Execute passed handler on resolve (result value will be passed),
   * or on reject (null as result value will be passed).
   */
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

  /**
   * @deprecated Use {@link #processed(Promise)}
   */
  @Deprecated
  default void notify(@NotNull AsyncPromise<? super T> child) {
    processed(child);
  }
}