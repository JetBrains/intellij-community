// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class NullableLazyValue<T> {
  private boolean myComputed;
  private @Nullable T myValue;

  protected abstract @Nullable T compute();

  @SuppressWarnings("DuplicatedCode")
  public @Nullable T getValue() {
    T value = myValue;
    if (!myComputed) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      value = compute();
      if (stamp.mayCacheNow()) {
        myValue = value;
        myComputed = true;
      }
    }
    return value;
  }

  public boolean isComputed() {
    return myComputed;
  }

  public static @NotNull <T> NullableLazyValue<T> lazyNullable(@NotNull Supplier<? extends T> value) {
    return new NullableLazyValue<T>() {
      @Override
      protected @Nullable T compute() {
        return value.get();
      }
    };
  }

  @SuppressWarnings("deprecation")
  public static @NotNull <T> NullableLazyValue<T> atomicLazyNullable(@NotNull Supplier<? extends T> value) {
    return new AtomicNullableLazyValue<T>() {
      @Override
      protected @Nullable T compute() {
        return value.get();
      }
    };
  }

  /**
   * NOTE: Assumes that values computed by different threads are equal and interchangeable
   * and readers should be ready to get different instances on different invocations of the {@link #getValue()}.
   */
  @SuppressWarnings("deprecation")
  public static @NotNull <T> NullableLazyValue<T> volatileLazyNullable(@NotNull Supplier<? extends T> value) {
    return new VolatileNullableLazyValue<T>() {
      @Override
      protected @Nullable T compute() {
        return value.get();
      }
    };
  }

  /** @deprecated please use {@link NullableLazyValue#lazyNullable} instead */
  @Deprecated
  public static @NotNull <T> NullableLazyValue<T> createValue(@NotNull Factory<? extends T> value) {
    return new NullableLazyValue<T>() {
      @Override
      protected @Nullable T compute() {
        return value.create();
      }
    };
  }
}
