// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Use {@link Supplier} instead.
 */
public interface Computable<T> extends Supplier<T> {
  T compute();

  @Override
  default T get() {
    return compute();
  }

  final class PredefinedValueComputable<T> implements Computable<T> {
    private final T myValue;

    public PredefinedValueComputable(@Nullable T value) {
      myValue = value;
    }

    @Override
    public T compute() {
      return myValue;
    }

    @Override
    public String toString() {
      return "PredefinedValueComputable{" + myValue + "}";
    }
  }

  /**
   * @deprecated Use {@link NotNullLazyValue}::getValue instead
   */
  @Deprecated
  abstract class NotNullCachedComputable<T> implements NotNullComputable<T> {
    private T myValue;

    @NotNull
    protected abstract T internalCompute();

    @NotNull
    @Override
    public final T compute() {
      T value = myValue;
      if (value == null) {
        myValue = value = internalCompute();
      }
      return value;
    }
  }
}
