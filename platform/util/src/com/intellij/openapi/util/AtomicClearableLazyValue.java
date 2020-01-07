// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

public abstract class AtomicClearableLazyValue<T> extends ClearableLazyValue<T> {
  /** @noinspection MethodOverridesStaticMethodOfSuperclass*/
  @NotNull
  public static <T> AtomicClearableLazyValue<T> create(@NotNull Computable<? extends T> computable) {
    return new AtomicClearableLazyValue<T>() {
      @NotNull
      @Override
      protected T compute() {
        return computable.compute();
      }
    };
  }

  @NotNull
  @Override
  public final synchronized T getValue() {
    return super.getValue();
  }

  @Override
  public final synchronized void drop() {
    super.drop();
  }
}
