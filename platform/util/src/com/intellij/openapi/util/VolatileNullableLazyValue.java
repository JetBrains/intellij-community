// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public abstract class VolatileNullableLazyValue<T> extends NullableLazyValue<T> {
  private volatile boolean myComputed;
  private volatile @Nullable T myValue;

  /** @deprecated please use {@link NullableLazyValue#volatileLazyNullable} instead */
  @Deprecated
  protected VolatileNullableLazyValue() { }

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
