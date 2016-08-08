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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AsyncValueLoader<T> {
  private final AtomicReference<Promise<T>> ref = new AtomicReference<>();

  private volatile long modificationCount;
  private volatile long loadedModificationCount;

  private final Consumer<T> doneHandler = new Consumer<T>() {
    @Override
    public void consume(T o) {
      loadedModificationCount = modificationCount;
    }
  };

  @NotNull
  public final Promise<T> get() {
    return get(true);
  }

  public final T getResult() {
    //noinspection unchecked
    return ((Getter<T>)get(true)).get();
  }

  public final void reset() {
    Promise<T> oldValue = ref.getAndSet(null);
    if (oldValue instanceof AsyncPromise) {
      rejectAndDispose((AsyncPromise<T>)oldValue);
    }
  }

  private void rejectAndDispose(@NotNull AsyncPromise<T> asyncResult) {
    try {
      asyncResult.setError("rejected");
    }
    finally {
      T result = asyncResult.get();
      if (result != null) {
        disposeResult(result);
      }
    }
  }

  protected void disposeResult(@NotNull T result) {
    if (result instanceof Disposable) {
      Disposer.dispose((Disposable)result, false);
    }
  }

  public final boolean has() {
    Promise<T> result = ref.get();
    //noinspection unchecked
    return result != null && result.getState() == Promise.State.FULFILLED && ((Getter<T>)result).get() != null;
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
      else if (state == Promise.State.FULFILLED) {
        //noinspection unchecked
        if (!checkFreshness || isUpToDate(((Getter<T>)promise).get())) {
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

    effectivePromise.done(doneHandler);
    if (isCancelOnReject()) {
      effectivePromise.rejected(new Consumer<Throwable>() {
        @Override
        public void consume(Throwable throwable) {
          ref.compareAndSet(effectivePromise, null);
        }
      });
    }

    if (effectivePromise != promise) {
      effectivePromise.notify(promise);
    }
    return effectivePromise;
  }

  @NotNull
  protected abstract Promise<T> load(@NotNull AsyncPromise<T> result) throws IOException;

  protected boolean isUpToDate(@Nullable T result) {
    return loadedModificationCount == modificationCount;
  }

  public final void set(@NotNull T result) {
    Promise<T> oldValue = ref.getAndSet(Promise.resolve(result));
    if (oldValue != null && oldValue instanceof AsyncPromise) {
      rejectAndDispose((AsyncPromise<T>)oldValue);
    }
  }

  public final void markDirty() {
    modificationCount++;
  }
}