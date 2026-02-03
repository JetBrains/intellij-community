// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.concurrency;

import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@ApiStatus.Internal
public abstract class PromiseManager<HOST, VALUE> {
  private final AtomicFieldUpdater<HOST, Promise<VALUE>> fieldUpdater;

  @SuppressWarnings("UnusedDeclaration")
  public PromiseManager(@NotNull AtomicFieldUpdater<HOST, Promise<VALUE>> fieldUpdater) {
    this.fieldUpdater = fieldUpdater;
  }

  public PromiseManager(@NotNull Class<HOST> ownerClass) {
    //noinspection unchecked
    fieldUpdater = ((AtomicFieldUpdater)AtomicFieldUpdater.forFieldOfType(ownerClass, Promise.class));
  }

  public boolean isUpToDate(@NotNull HOST host, @NotNull VALUE value) {
    return true;
  }

  public abstract @NotNull Promise<VALUE> load(@NotNull HOST host);

  public final void reset(HOST host) {
    fieldUpdater.setVolatile(host, null);
  }

  public final void set(HOST host, @Nullable VALUE value) {
    if (value == null) {
      reset(host);
    }
    else {
      ((AsyncPromise<VALUE>)getOrCreateAsyncResult(host, false, false)).setResult(value);
    }
  }

  public final boolean has(HOST host) {
    Promise<VALUE> result = fieldUpdater.getVolatile(host);
    return result != null && result.isSucceeded();
  }

  public final @Nullable Promise.State getState(HOST host) {
    Promise<VALUE> result = fieldUpdater.getVolatile(host);
    return result == null ? null : result.getState();
  }

  public final @NotNull Promise<VALUE> get(HOST host) {
    return get(host, true);
  }

  public final @NotNull Promise<VALUE> get(HOST host, boolean checkFreshness) {
    return getOrCreateAsyncResult(host, checkFreshness, true);
  }

  private @NotNull Promise<VALUE> getOrCreateAsyncResult(HOST host, boolean checkFreshness, boolean load) {
    Promise<VALUE> promise = fieldUpdater.getVolatile(host);
    if (promise == null) {
      promise = new AsyncPromise<>();
      promise.onError((ignored) -> {});
      if (!fieldUpdater.compareAndSet(host, null, promise)) {
        return fieldUpdater.getVolatile(host);
      }
    }
    else {
      Promise.State state = promise.getState();
      if (state == Promise.State.PENDING) {
        // if current promise is not processed, so, we don't need to check cache state
        return promise;
      }
      else if (state == Promise.State.SUCCEEDED) {
        try {
          if (!checkFreshness || isUpToDate(host, promise.blockingGet(0))) {
            return promise;
          }
        }
        catch (ExecutionException | TimeoutException e) {
          throw new RuntimeException(e);
        }

        if (!fieldUpdater.compareAndSet(host, promise, promise = new AsyncPromise<>())) {
          Promise<VALUE> valueFromAnotherThread = fieldUpdater.getVolatile(host);
          while (valueFromAnotherThread == null) {
            if (fieldUpdater.compareAndSet(host, null, promise)) {
              return getPromise(host, load, promise);
            }
            else {
              valueFromAnotherThread = fieldUpdater.getVolatile(host);
            }
          }
          return valueFromAnotherThread;
        }
      }
    }

    return getPromise(host, load, promise);
  }

  private @NotNull Promise<VALUE> getPromise(HOST host, boolean load, Promise<VALUE> promise) {
    if (!load || promise.getState() != Promise.State.PENDING) {
      return promise;
    }

    Promise<VALUE> effectivePromise = load(host);
    if (effectivePromise != promise) {
      fieldUpdater.compareAndSet(host, promise, effectivePromise);
      effectivePromise.processed(promise);
    }
    return effectivePromise;
  }
}