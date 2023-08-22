// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Use {@link com.intellij.util.concurrency.SynchronizedClearableLazy} instead.
 */
public abstract class ClearableLazyValue<T> implements Supplier<T> {
  public static @NotNull <T> ClearableLazyValue<T> create(@NotNull Supplier<? extends @NotNull T> computable) {
    return new ClearableLazyValue<T>() {
      @Override
      protected @NotNull T compute() {
        return computable.get();
      }
    };
  }

  public static @NotNull <T> ClearableLazyValue<T> createAtomic(@NotNull Supplier<? extends @NotNull T> computable) {
    return new AtomicClearableLazyValue<T>() {
      @Override
      protected @NotNull T compute() {
        return computable.get();
      }
    };
  }

  private T myValue;

  @Override
  public final T get() {
    return getValue();
  }

  protected abstract @NotNull T compute();

  public @NotNull T getValue() {
    T result = myValue;
    if (result == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      result = compute();
      if (stamp.mayCacheNow()) {
        myValue = result;
      }
    }
    return result;
  }

  public boolean isCached() {
    return myValue != null;
  }

  public void drop() {
    myValue = null;
  }
}
