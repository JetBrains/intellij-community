/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class FixedFuture<T> implements Future<T> {
  private final T myValue;
  private final Throwable myThrowable;

  public FixedFuture(T value) {
    myValue = value;
    myThrowable = null;
  }

  private FixedFuture(@NotNull Throwable throwable) {
    myValue = null;
    myThrowable = throwable;
  }

  public static <T> FixedFuture<T> completeExceptionally(@NotNull Throwable throwable) {
    return new FixedFuture<T>(throwable);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return true;
  }

  @Override
  public T get() throws ExecutionException {
    if (myThrowable != null) {
      throw new ExecutionException(myThrowable);
    }
    return myValue;
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit) {
    return myValue;
  }
}
