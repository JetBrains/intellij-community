// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.Nullable;

/**
 * Deprecated. Use {@link java.util.function.Function} with {@code @Nullable} annotation on the second type parameter instead.
 */
public interface NullableFunction<Param, Result> extends Function<Param, Result> {
  @Override
  @Nullable
  Result fun(final Param param);
}
