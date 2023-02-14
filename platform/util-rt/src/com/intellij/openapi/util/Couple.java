// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public class Couple<T> extends Pair<T, T> {
  private static final Couple<Object> EMPTY_COUPLE = new Couple<>(null, null);

  public Couple(T first, T second) {
    super(first, second);
  }

  @NotNull
  public static <T> Couple<T> of(T first, T second) {
    return new Couple<>(first, second);
  }

  @NotNull
  public static <T> Couple<T> getEmpty() {
    //noinspection unchecked
    return (Couple<T>)EMPTY_COUPLE;
  }
}
