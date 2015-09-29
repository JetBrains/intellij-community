/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.concurrency;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 16-Sep-15
 */
public class BoundedTaskExecutorService extends BoundedTaskExecutor implements ExecutorService{

  public BoundedTaskExecutorService(@NotNull ExecutorService backendExecutor, int maxSimultaneousTasks) {
    super(backendExecutor, maxSimultaneousTasks);
  }

  @Override
  public void shutdown() {
    getBackendExecutor().shutdown();
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    return getBackendExecutor().shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return getBackendExecutor().isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return getBackendExecutor().isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return getBackendExecutor().awaitTermination(timeout, unit);
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    final List<Future<T>> futures = new SmartList<Future<T>>();
    for (Callable<T> task : tasks) {
      futures.add(submit(task));
    }
    boolean done = false;
    try {
      for (Future<T> future : futures) {
        if (!future.isDone()) {
          try {
            future.get();
          }
          catch (ExecutionException ignored) {
          }
        }
      }
      done = true;
      return futures;
    }
    finally {
      if (!done) {
        for (Future<T> future : futures) {
          future.cancel(true);
        }
      }
    }
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
    long nanos = unit.toNanos(timeout);
    final List<Future<T>> futures = new SmartList<Future<T>>();

    boolean done = false;
    try {
      long lastCheck = System.nanoTime();

      for (Callable<T> task : tasks) {
        futures.add(submit(task));
      }

      long now = System.nanoTime();
      nanos -= now - lastCheck;
      if (nanos <= 0) {
        return futures;
      }
      lastCheck = now;

      for (Future<T> future : futures) {
        if (!future.isDone()) {
          if (nanos <= 0) {
            return futures;
          }
          try {
            future.get(nanos, TimeUnit.NANOSECONDS);
          }
          catch (ExecutionException ignored) {
          }
          catch (TimeoutException e) {
            return futures;
          }
          now = System.nanoTime();
          nanos -= now - lastCheck;
          lastCheck = now;
        }
      }
      done = true;
      return futures;
    }
    finally {
      if (!done) {
        for (Future<T> future : futures) {
          future.cancel(true);
        }
      }
    }
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    throw new RuntimeException("invokeAny is not supported by this ExecutorService implementation");
  }


  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException {
    throw new RuntimeException("invokeAny is not supported by this ExecutorService implementation");
  }

  @NotNull
  private ExecutorService getBackendExecutor() {
    return (ExecutorService)myBackendExecutor;
  }

}
