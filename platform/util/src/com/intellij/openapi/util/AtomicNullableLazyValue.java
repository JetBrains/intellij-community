// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public abstract class AtomicNullableLazyValue<T> extends NullableLazyValue<T> {
  private volatile T myValue;
  private volatile boolean myComputed;

  /** @deprecated please use {@link NullableLazyValue#atomicLazyNullable} instead */
  @Deprecated
  protected AtomicNullableLazyValue() { }

  @Override
  public final T getValue() {
    boolean computed = myComputed;
    T value = myValue;
    if (computed) {
      return value;
    }
    //noinspection SynchronizeOnThis
    synchronized (this) {
      computed = myComputed;
      value = myValue;
      if (!computed) {
        RecursionGuard.StackStamp stamp = RecursionManager.markStack();
        value = compute();
        if (stamp.mayCacheNow()) {
          myValue = value;
          myComputed = true;
        }
      }
    }
    return value;
  }

  @Override
  public boolean isComputed() {
    return myComputed;
  }

  /** @deprecated please use {@link NullableLazyValue#atomicLazyNullable} instead */
  @Deprecated
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static @NotNull <T> AtomicNullableLazyValue<T> createValue(@NotNull Factory<? extends T> value) {
    return new AtomicNullableLazyValue<T>() {
      @Override
      protected @Nullable T compute() {
        return value.create();
      }
    };
  }
}
