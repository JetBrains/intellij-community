// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public abstract class KeyWithDefaultValue<T> extends Key<T> {
  public KeyWithDefaultValue(@NotNull @NonNls String name) {
    super(name);
  }

  public abstract T getDefaultValue();

  @NotNull
  public static <T> KeyWithDefaultValue<T> create(@NotNull @NonNls String name, T defValue) {
    return new KeyWithDefaultValue<T>(name) {
      @Override
      public T getDefaultValue() {
        return defValue;
      }
    };
  }

  @NotNull
  public static <T> KeyWithDefaultValue<T> create(@NotNull @NonNls String name, @NotNull Supplier<? extends T> factory) {
    return new KeyWithDefaultValue<T>(name) {
      @Override
      public T getDefaultValue() {
        return factory.get();
      }
    };
  }
}
