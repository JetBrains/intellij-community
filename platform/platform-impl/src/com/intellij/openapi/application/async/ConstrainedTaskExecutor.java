// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * @author eldar
 */
public class ConstrainedTaskExecutor {
  private final @NotNull ConstrainedExecutionEx<?> myConstraintExecution;

  public ConstrainedTaskExecutor(@NotNull ConstrainedExecutionEx<?> execution) {
    myConstraintExecution = execution;
  }

  public void execute(@NotNull Runnable command) {
    final Expiration expiration = myConstraintExecution.composeExpiration();
    final BooleanSupplier condition = (expiration == null) ? null : () -> !expiration.isExpired();
    final Executor executor = myConstraintExecution.createConstraintSchedulingExecutor(condition);
    executor.execute(command);
  }

  public CancellablePromise<Void> submit(@NotNull Runnable task) {
    return submit(() -> {
      task.run();
      return null;
    });
  }

  public <T> CancellablePromise<T> submit(@NotNull Callable<? extends T> task) {
    final Expiration expiration = myConstraintExecution.composeExpiration();

    final AsyncPromise<T> promise = new AsyncPromise<>();
    if (expiration != null) {
      final Expiration.Handle expirationHandle = expiration.invokeOnExpiration(() -> {
        // to avoid executing arbitrary code from inside the expiration handler
        myConstraintExecution.dispatchLaterUnconstrained(promise::cancel);
      });
      promise.onProcessed(value -> expirationHandle.unregisterHandler());
    }

    final Executor executor = myConstraintExecution.createConstraintSchedulingExecutor(() -> !promise.isCancelled());
    executor.execute(() -> {
      try {
        final T result = task.call();
        promise.setResult(result);
      }
      catch (Throwable e){
        promise.setError(e);
      }
    });
    return promise;
  }
}
