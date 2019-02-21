// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/**
 * @author eldar
 */
public class ConstrainedTaskExecutor {
  private final @NotNull ConstrainedExecutionScheduler myExecutionScheduler;
  private final @Nullable Expiration myExpiration;

  public ConstrainedTaskExecutor(@NotNull ConstrainedExecutionScheduler executionScheduler,
                                 @Nullable Expiration expiration) {
    myExecutionScheduler = executionScheduler;
    myExpiration = expiration;
  }

  public void execute(@NotNull Runnable command) {
    final BooleanSupplier condition = (myExpiration == null) ? null : () -> !myExpiration.isExpired();
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

    final BooleanSupplier condition = () -> !(myExpiration != null && myExpiration.isExpired() || promise.isCancelled());
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
