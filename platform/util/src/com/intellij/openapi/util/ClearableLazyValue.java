// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Lazy value with ability to reset (and recompute) the (not-null) value.
 * Thread-safe version: {@link AtomicClearableLazyValue}.
 */
public abstract class ClearableLazyValue<T> {
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
