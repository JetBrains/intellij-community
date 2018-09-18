// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class VolatileNullableLazyValue<T> extends NullableLazyValue<T> {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("VolatileNullableLazyValue");
  private volatile boolean myComputed;
  @Nullable private volatile T myValue;

  @Override
  @Nullable
  protected abstract T compute();

  @Override
  @Nullable
  public T getValue() {
    T value = myValue;
    if (!myComputed) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      value = compute();
      if (stamp.mayCacheNow()) {
        myValue = value;
        myComputed = true;
      }
    }
    return value;
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @NotNull
  public static <T> VolatileNullableLazyValue<T> createValue(@NotNull final Factory<T> value) {
    return new VolatileNullableLazyValue<T>() {

      @Nullable
      @Override
      protected T compute() {
        return value.create();
      }
    };
  }

}
