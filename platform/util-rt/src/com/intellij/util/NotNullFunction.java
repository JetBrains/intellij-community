// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

/**
 * Deprecated. Use {@link java.util.function.Function} with {@code @NotNull} annotation on the second type parameter instead.
 */
public interface NotNullFunction<Param, Result> extends NullableFunction<Param, Result> {
  @Override
  @NotNull
  Result fun(final Param dom);
}