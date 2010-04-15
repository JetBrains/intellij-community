package com.intellij.util.concurrency;

import com.intellij.openapi.util.Ref;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Semaphore;

public class FutureResult<T> implements Future<T> {
  private final Semaphore mySema = new Semaphore(0);
  private volatile Ref<T> myValue;

  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  public boolean isCancelled() {
    return false;
  }

  public boolean isDone() {
    return mySema.availablePermits() > 0;
  }

  public void set(T result) {
    assert myValue == null;
    
    myValue = new Ref<T>(result);
    mySema.release();
  }

  public T get() throws InterruptedException, ExecutionException {
    try {
      mySema.acquire();
      return myValue.get();
    }
    finally {
      mySema.release();
    }
  }

  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    try {
      if (!mySema.tryAcquire(timeout, unit)) throw new TimeoutException();
      return myValue.get();
    }
    finally {
      mySema.release();
    }
  }
}
