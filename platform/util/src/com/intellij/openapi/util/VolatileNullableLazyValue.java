// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * NOTE: Assumes that values computed by different threads are equal and interchangeable
 * and readers should be ready to get different instances on different invocations of the {@link #getValue()}
 *
 * @author peter
 */
public abstract class VolatileNullableLazyValue<T> extends NullableLazyValue<T> {
  private volatile boolean myComputed;
  @Nullable private volatile T myValue;

  @Override
  @Nullable
  protected abstract T compute();

  @Override
  @Nullable
  public T getValue() {
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

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @NotNull
  public static <T> VolatileNullableLazyValue<T> createValue(@NotNull final Factory<? extends T> value) {
    return new VolatileNullableLazyValue<T>() {

      @Nullable
      @Override
      protected T compute() {
        return value.create();
      }
    };
  }

}
