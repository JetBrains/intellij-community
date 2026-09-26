// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * Obsolete, use {@link java.util.function.Function} instead.
 */
@ApiStatus.Obsolete
@FunctionalInterface
public interface Function<Param, Result> extends java.util.function.Function<Param, Result> {
  Result fun(Param param);

  @ApiStatus.Obsolete
  interface Mono<T> extends Function<T, T> {}

  @Override
  default Result apply(Param param) {
    return fun(param);
  }
}
