// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class NullableLazyValue<T> {
  private boolean myComputed;
  @Nullable private T myValue;

  @Nullable
  protected abstract T compute();

  @Nullable
  public T getValue() {
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

  @NotNull
  public static <T> NullableLazyValue<T> createValue(@NotNull final Factory<? extends T> value) {
    return new NullableLazyValue<T>() {

      @Nullable
      @Override
      protected T compute() {
        return value.create();
      }
    };
  }

  public boolean isComputed() {
    return myComputed;
  }
}
