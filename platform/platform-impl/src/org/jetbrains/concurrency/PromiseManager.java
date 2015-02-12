package org.jetbrains.concurrency;

import com.intellij.openapi.util.Getter;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public abstract Promise<VALUE> load(@NotNull HOST host);

  public final void reset(HOST host) {
    fieldUpdater.set(host, null);
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
    Promise<VALUE> result = fieldUpdater.get(host);
    return result != null && result.getState() == Promise.State.FULFILLED;
  }

  @Nullable
  public final Promise.State getState(HOST host) {
    Promise<VALUE> result = fieldUpdater.get(host);
    return result == null ? null : result.getState();
  }

  @NotNull
  public final Promise<VALUE> get(HOST host) {
    return get(host, true);
  }

  @NotNull
  public final Promise<VALUE> get(HOST host, boolean checkFreshness) {
    return getOrCreateAsyncResult(host, checkFreshness, true);
  }

  @NotNull
  private Promise<VALUE> getOrCreateAsyncResult(HOST host, boolean checkFreshness, boolean load) {
    Promise<VALUE> promise = fieldUpdater.get(host);
    if (promise == null) {
      if (!fieldUpdater.compareAndSet(host, null, promise = new AsyncPromise<VALUE>())) {
        return fieldUpdater.get(host);
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
        if (!checkFreshness || isUpToDate(host, ((Getter<VALUE>)promise).get())) {
          return promise;
        }

        if (!fieldUpdater.compareAndSet(host, promise, promise = new AsyncPromise<VALUE>())) {
          Promise<VALUE> valueFromAnotherThread = fieldUpdater.get(host);
          while (valueFromAnotherThread == null) {
            if (fieldUpdater.compareAndSet(host, null, promise)) {
              return getPromise(host, load, promise);
            }
            else {
              valueFromAnotherThread = fieldUpdater.get(host);
            }
          }
          return valueFromAnotherThread;
        }
      }
    }

    return getPromise(host, load, promise);
  }

  @NotNull
  private Promise<VALUE> getPromise(HOST host, boolean load, Promise<VALUE> promise) {
    if (!load) {
      return promise;
    }

    Promise<VALUE> effectivePromise = load(host);
    fieldUpdater.compareAndSet(host, promise, effectivePromise);
    effectivePromise.notify((AsyncPromise<VALUE>)promise);
    return effectivePromise;
  }
}