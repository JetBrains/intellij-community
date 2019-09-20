// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface ValueKey<T> {
  @NotNull
  String getName();

  default BeforeWhen<T> match() {
    return new ValueMatcherImpl<>(getName());
  }

  static <T> BeforeWhen<T> match(String name) {
    return new ValueMatcherImpl<>(name);
  }

  interface BeforeWhen<T> {
    @NotNull <TT> BeforeThen<T, TT> when(ValueKey<TT> key);
    T get();
    @Nullable
    T orNull();
  }

  interface BeforeThen<T, TT> {
    @NotNull
    BeforeThen<T, TT> or(ValueKey<TT> key);
    @NotNull
    BeforeWhen<T> then(TT value);
    @NotNull
    BeforeWhen<T> thenGet(@NotNull Supplier<? extends TT> fn);
  }
}
