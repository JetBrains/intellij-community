// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class AtomicNullableLazyValue<T> extends NullableLazyValue<T> {
  private volatile T myValue;
  private volatile boolean myComputed;

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
  @NotNull
  public static <T> AtomicNullableLazyValue<T> createValue(@NotNull final Factory<? extends T> value) {
    return new AtomicNullableLazyValue<T>() {
      @Nullable
      @Override
      protected T compute() {
        return value.create();
      }
    };
  }
}
