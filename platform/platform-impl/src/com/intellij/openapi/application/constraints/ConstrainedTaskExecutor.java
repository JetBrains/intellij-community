// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.constraints;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * @author eldar
 */
public final class ConstrainedTaskExecutor implements Executor {
  private final @NotNull ConstrainedExecutionScheduler myExecutionScheduler;
  private final @Nullable BooleanSupplier myCancellationCondition;
  private final @Nullable Expiration myExpiration;

  public ConstrainedTaskExecutor(@NotNull ConstrainedExecutionScheduler executionScheduler,
                                 @Nullable BooleanSupplier cancellationCondition,
                                 @Nullable Expiration expiration) {
    myExecutionScheduler = executionScheduler;
    myCancellationCondition = cancellationCondition;
    myExpiration = expiration;
  }

  @Override
  public void execute(@NotNull Runnable command) {
    final BooleanSupplier condition = ((myExpiration == null) && (myCancellationCondition == null)) ? null : () -> {
      if (myExpiration != null && myExpiration.isExpired()) return false;
      if (myCancellationCondition != null && myCancellationCondition.getAsBoolean()) return false;
      return true;
    };
    myExecutionScheduler.scheduleWithinConstraints(command, condition);
  }

  public CancellablePromise<Void> submit(@NotNull Runnable task) {
    return submit(() -> {
      task.run();
      return null;
    });
  }

  public <T> CancellablePromise<T> submit(@NotNull Callable<? extends T> task) {
    final AsyncPromise<T> promise = new AsyncPromise<>();
    if (myExpiration != null) {
      final Expiration.Handle expirationHandle = myExpiration.invokeOnExpiration(promise::cancel);
      promise.onProcessed(value -> expirationHandle.unregisterHandler());
    }

    final BooleanSupplier condition = () -> {
      if (promise.isCancelled()) return false;
      if (myExpiration != null && myExpiration.isExpired()) return false;
      if (myCancellationCondition != null && myCancellationCondition.getAsBoolean()) {
        promise.cancel();
        return false;
      }
      return true;
    };
    myExecutionScheduler.scheduleWithinConstraints(() -> {
      try {
        final T result = task.call();
        promise.setResult(result);
      }
      catch (Throwable e){
        promise.setError(e);
      }
    }, condition);
    return promise;
  }
}
