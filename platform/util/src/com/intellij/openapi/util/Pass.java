// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Use {@link Consumer} instead
 */
@ApiStatus.Obsolete
public abstract class Pass<T> implements Consumer<T> {
  public abstract void pass(T t);

  @Override
  public void accept(T t) {
    pass(t);
  }

  public static <T> @NotNull Pass<T> create(@NotNull Consumer<? super T> consumer) {
    return new Pass<T>() {
      @Override
      public void pass(T o) {
        consumer.accept(o);
      }
    };
  }
}