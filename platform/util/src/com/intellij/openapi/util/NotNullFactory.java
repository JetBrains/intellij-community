// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Obsolete, use {@link java.util.function.Supplier} with {@code @NotNull} annotation on the type parameter instead.
 */
@ApiStatus.Obsolete
@FunctionalInterface
public interface NotNullFactory<T> extends Factory<@NotNull T> {
  @Override
  @NotNull
  T create();
}
