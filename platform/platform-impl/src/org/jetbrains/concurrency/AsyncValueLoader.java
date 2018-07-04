// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class AsyncValueLoader<T> {
  private final AtomicReference<Promise<T>> ref = new AtomicReference<>();

  private volatile long modificationCount;
  private volatile long loadedModificationCount;

  private final Consumer<T> doneHandler = new Consumer<T>() {
    @Override
    public void accept(T o) {
      loadedModificationCount = modificationCount;
    }
  };

  @NotNull
  public final Promise<T> get() {
    return get(true);
  }

  public final T getResultIfFullFilled() {
    Promise<T> result = ref.get();
    try {
      return (result != null && result.isSucceeded()) ? result.blockingGet(0) : null;
    }
    catch (TimeoutException | ExecutionException e) {
      return null;
    }
  }

  public final void reset() {
    Promise<T> oldValue = ref.getAndSet(null);
    if (oldValue instanceof AsyncPromise) {
      rejectAndDispose((AsyncPromise<T>)oldValue);
    }
  }

  private void rejectAndDispose(@NotNull AsyncPromise<T> asyncResult) {
    if (asyncResult.setError("rejected")) {
      return;
    }

    T result;
    try {
      result = asyncResult.blockingGet(0);
    }
    catch (TimeoutException e) {
      throw new RuntimeException(e);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }

    if (result != null) {
      disposeResult(result);
    }
  }

  protected void disposeResult(@NotNull T result) {
    if (result instanceof Disposable) {
      Disposer.dispose((Disposable)result, false);
    }
  }

  @NotNull
  public final Promise<T> get(boolean checkFreshness) {
    Promise<T> promise = ref.get();
    if (promise == null) {
      if (!ref.compareAndSet(null, promise = new AsyncPromise<>())) {
        return ref.get();
      }
    }
    else {
      Promise.State state = promise.getState();
      if (state == Promise.State.PENDING) {
        // if current promise is not processed, so, we don't need to check cache state
        return promise;
      }
      else if (state == Promise.State.SUCCEEDED) {
        //noinspection unchecked
        if (!checkFreshness || isUpToDate()) {
          return promise;
        }

        if (!ref.compareAndSet(promise, promise = new AsyncPromise<>())) {
          Promise<T> valueFromAnotherThread = ref.get();
          while (valueFromAnotherThread == null) {
            if (ref.compareAndSet(null, promise)) {
              return getPromise((AsyncPromise<T>)promise);
            }
            else {
              valueFromAnotherThread = ref.get();
            }
          }
          return valueFromAnotherThread;
        }
      }
    }

    return getPromise((AsyncPromise<T>)promise);
  }

  /**
   * if result was rejected, by default this result will not be canceled - call get() will return rejected result instead of attempt to load again,
   * but you can change this behavior - return true if you want to cancel result on reject
   */
  protected boolean isCancelOnReject() {
    return false;
  }

  @NotNull
  private Promise<T> getPromise(@NotNull AsyncPromise<T> promise) {
    final Promise<T> effectivePromise;
    try {
      effectivePromise = load(promise);
      if (effectivePromise != promise) {
        ref.compareAndSet(promise, effectivePromise);
      }
    }
    catch (Throwable e) {
      ref.compareAndSet(promise, null);
      rejectAndDispose(promise);
      //noinspection InstanceofCatchParameter
      throw e instanceof RuntimeException ? ((RuntimeException)e) : new RuntimeException(e);
    }

    effectivePromise.onSuccess(doneHandler);
    if (isCancelOnReject()) {
      effectivePromise.onError(throwable -> ref.compareAndSet(effectivePromise, null));
    }

    if (effectivePromise != promise) {
      effectivePromise.processed(promise);
    }
    return effectivePromise;
  }

  @NotNull
  protected abstract Promise<T> load(@NotNull AsyncPromise<T> result) throws IOException;

  private boolean isUpToDate() {
    return loadedModificationCount == modificationCount;
  }

  public final void set(@NotNull T result) {
    Promise<T> oldValue = ref.getAndSet(Promise.resolve(result));
    if (oldValue instanceof AsyncPromise) {
      rejectAndDispose((AsyncPromise<T>)oldValue);
    }
  }

  public final void markDirty() {
    modificationCount++;
  }
}