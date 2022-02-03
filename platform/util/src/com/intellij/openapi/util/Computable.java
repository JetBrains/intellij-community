// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Deprecated. Use {@link java.util.function.Supplier} instead.
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
}
