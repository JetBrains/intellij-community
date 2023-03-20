// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Deprecated. Use {@link java.util.function.Consumer} with {@code @Nullable} annotation on the type parameter instead.
 */
@ApiStatus.Obsolete
public interface NullableConsumer<T> extends Consumer<T> {
  void consume(@Nullable T t);
}
