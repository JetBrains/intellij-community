// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Deprecated. Use {@link java.util.function.Function} with {@code @Nullable} annotation on the second type parameter instead.
 */
@ApiStatus.Obsolete
@FunctionalInterface
public interface NullableFunction<Param, Result> extends Function<Param, @Nullable Result>, java.util.function.Function<Param, @Nullable Result> {
  @Override
  @Nullable
  Result fun(final Param param);

  @Override
  default @Nullable Result apply(Param param) {
    return fun(param);
  }
}
