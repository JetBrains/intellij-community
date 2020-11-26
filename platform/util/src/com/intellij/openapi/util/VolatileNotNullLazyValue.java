// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

@ApiStatus.NonExtendable
public abstract class VolatileNotNullLazyValue<T> extends NotNullLazyValue<T> {
  private volatile T myValue;

  @Override
  @NotNull
  public final T getValue() {
    T value = myValue;
    if (value == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      value = compute();
      if (stamp.mayCacheNow()) {
        myValue = value;
      }
    }
    return value;
  }

  @Override
  public boolean isComputed() {
    return myValue != null;
  }

  /**
   * @deprecated Use {@link NotNullLazyValue#volatileLazy(Supplier)}
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @NotNull
  @Deprecated
  public static <T> VolatileNotNullLazyValue<T> createValue(@NotNull NotNullFactory<? extends T> value) {
    return (VolatileNotNullLazyValue<T>)NotNullLazyValue.volatileLazy(() -> value);
  }
}