// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 * Deprecated. Use {@link java.util.function.Function} instead.
 */
@ApiStatus.Obsolete
public final class NullableConstantFunction<Param, Result> implements NullableFunction<Param, Result> {
  private final Result value;

  public NullableConstantFunction(@Nullable Result value) {
    this.value = value;
  }

  @Override
  public @Nullable Result fun(Param param) {
    return value;
  }
}
