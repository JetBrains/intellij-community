// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

@ApiStatus.NonExtendable
public abstract class AtomicNotNullLazyValue<T> extends NotNullLazyValue<T> {
  private volatile T myValue;

  /** @deprecated Use {@link NotNullLazyValue#atomicLazy(Supplier)} */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated
  protected AtomicNotNullLazyValue() { }

  @Override
  @NotNull
  public final T getValue() {
    T value = myValue;
    if (value == null) {
      //noinspection SynchronizeOnThis
      synchronized (this) {
        value = myValue;
        if (value == null) {
          RecursionGuard.StackStamp stamp = RecursionManager.markStack();
          value = compute();
          if (stamp.mayCacheNow()) {
            myValue = value;
          }
        }
      }
    }
    return value;
  }

  @Override
  public boolean isComputed() {
    return myValue != null;
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unchecked"})
  public static @NotNull <T> AtomicNotNullLazyValue<T> createValue(@NotNull NotNullFactory<? extends T> value) {
    return (AtomicNotNullLazyValue<T>)NotNullLazyValue.atomicLazy(() -> value.create());
  }
}
