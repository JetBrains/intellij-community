/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.concurrency;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Promise<T> {
  public static final Promise<Void> DONE = new DonePromise<>(null);
  public static final Promise<Void> REJECTED = new RejectedPromise<>(createError("rejected"));

  @NotNull
  public static RuntimeException createError(@NotNull String error) {
    return new MessageError(error);
  }

  public enum State {
    PENDING, FULFILLED, REJECTED
  }

  @NotNull
  public static <T> Promise<T> resolve(T result) {
    if (result == null) {
      //noinspection unchecked
      return (Promise<T>)DONE;
    }
    else {
      return new DonePromise<>(result);
    }
  }

  @NotNull
  public static <T> Promise<T> reject(@NotNull String error) {
    return reject(createError(error));
  }

  @NotNull
  public static <T> Promise<T> reject(@Nullable Throwable error) {
    if (error == null) {
      //noinspection unchecked
      return (Promise<T>)REJECTED;
    }
    else {
      return new RejectedPromise<>(error);
    }
  }

  @NotNull
  public static Promise<Void> wrapAsVoid(@NotNull ActionCallback asyncResult) {
    final AsyncPromise<Void> promise = new AsyncPromise<>();
    asyncResult.doWhenDone(() -> promise.setResult(null)).doWhenRejected(
      error -> promise.setError(createError(error == null ? "Internal error" : error)));
    return promise;
  }

  @NotNull
  public static <T> Promise<T> wrap(@NotNull AsyncResult<T> asyncResult) {
    final AsyncPromise<T> promise = new AsyncPromise<>();
    asyncResult.doWhenDone(new Consumer<T>() {
      @Override
      public void consume(T result) {
        promise.setResult(result);
      }
    }).doWhenRejected(promise::setError);
    return promise;
  }

  @NotNull
  public abstract Promise<T> done(@NotNull Consumer<? super T> done);

  @NotNull
  public abstract Promise<T> processed(@NotNull AsyncPromise<? super T> fulfilled);

  @NotNull
  public abstract Promise<T> rejected(@NotNull Consumer<Throwable> rejected);

  public abstract Promise<T> processed(@NotNull Consumer<? super T> processed);

  @NotNull
  public abstract <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<? super T, ? extends SUB_RESULT> done);

  @NotNull
  public abstract <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull Function<? super T, Promise<SUB_RESULT>> done);

  @NotNull
  public abstract State getState();

  @SuppressWarnings("ExceptionClassNameDoesntEndWithException")
  public static class MessageError extends RuntimeException {
    private final ThreeState log;

    public MessageError(@NotNull String error) {
      super(error);

      log = ThreeState.UNSURE;
    }

    public MessageError(@NotNull String error, boolean log) {
      super(error);

      this.log = ThreeState.fromBoolean(log);
    }

    @NotNull
    @Override
    public final synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  /**
   * Log error if not message error
   */
  public static boolean logError(@NotNull Logger logger, @NotNull Throwable e) {
    if (e instanceof MessageError) {
      ThreeState log = ((MessageError)e).log;
      if (log == ThreeState.YES || (log == ThreeState.UNSURE && ApplicationManager.getApplication().isUnitTestMode())) {
        logger.error(e);
        return true;
      }
    }
    else if (!(e instanceof ProcessCanceledException)) {
      logger.error(e);
      return true;
    }

    return false;
  }

  public abstract void notify(@NotNull AsyncPromise<? super T> child);
}