// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * NOTE: Assumes that values computed by different threads are equal and interchangeable
 * and readers should be ready to get different instances on different invocations of the {@link #getValue()}.
 */
public abstract class VolatileNullableLazyValue<T> extends NullableLazyValue<T> {
  private volatile boolean myComputed;
  private volatile @Nullable T myValue;

  @Override
  @SuppressWarnings("DuplicatedCode")
  public @Nullable T getValue() {
    boolean computed = myComputed;
    T value = myValue;
    if (!computed) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      value = compute();
      if (stamp.mayCacheNow()) {
        myValue = value;
        myComputed = true;
      }
    }
    return value;
  }

  @Override
  public boolean isComputed() {
    return myComputed;
  }

  /** @deprecated please use {@link NullableLazyValue#volatileLazyNullable} instead */
  @Deprecated
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static @NotNull <T> VolatileNullableLazyValue<T> createValue(@NotNull Factory<? extends T> value) {
    return new VolatileNullableLazyValue<T>() {
      @Override
      protected @Nullable T compute() {
        return value.create();
      }
    };
  }
}
