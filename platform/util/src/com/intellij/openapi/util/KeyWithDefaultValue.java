// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class KeyWithDefaultValue<T> extends Key<T> {
  public KeyWithDefaultValue(@NotNull @NonNls String name) {
    super(name);
  }

  public abstract T getDefaultValue();

  @NotNull
  public static <T> KeyWithDefaultValue<T> create(@NotNull @NonNls String name, final T defValue) {
    return new KeyWithDefaultValue<T>(name) {
      @Override
      public T getDefaultValue() {
        return defValue;
      }
    };
  }

  @NotNull
  public static <T> KeyWithDefaultValue<T> create(@NotNull @NonNls String name, @NotNull final Factory<T> factory) {
    return new KeyWithDefaultValue<T>(name) {
      @Override
      public T getDefaultValue() {
        return factory.create();
      }
    };
  }
}
