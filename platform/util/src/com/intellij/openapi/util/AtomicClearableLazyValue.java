// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

public abstract class AtomicClearableLazyValue<T> extends ClearableLazyValue<T> {
  /**
   * @deprecated Use {@link ClearableLazyValue#createAtomic}
   * @noinspection MethodOverridesStaticMethodOfSuperclass
   **/
  @NotNull
  @Deprecated
  public static <T> AtomicClearableLazyValue<T> create(@NotNull Computable<? extends T> computable) {
    //noinspection unchecked
    return (AtomicClearableLazyValue<T>)ClearableLazyValue.createAtomic(computable);
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
