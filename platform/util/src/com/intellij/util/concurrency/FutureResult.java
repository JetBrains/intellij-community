package com.intellij.util.concurrency;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;

import java.util.concurrent.*;
import java.util.concurrent.Semaphore;

public class FutureResult<T> implements Future<T> {
  private final Semaphore mySema = new Semaphore(0);
  private volatile Ref<Pair<Object, Boolean>> myValue;

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
    
    myValue = Ref.create(Pair.create((Object)result, true));
    mySema.release();
  }

  public void setException(Throwable e) {
    assert myValue == null;

    myValue = Ref.create(Pair.create((Object)e, false));
    mySema.release();
  }

  public T get() throws InterruptedException, ExecutionException {
    try {
      mySema.acquire();
      return doGet();
    }
    finally {
      mySema.release();
    }
  }

  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    try {
      if (!mySema.tryAcquire(timeout, unit)) throw new TimeoutException();
      return doGet();
    }
    finally {
      mySema.release();
    }
  }

  private T doGet() throws ExecutionException {
    Pair<Object, Boolean> pair = myValue.get();
    if (!pair.second) throw new ExecutionException((Throwable)pair.first);
    return (T)pair.first;
  }
}
