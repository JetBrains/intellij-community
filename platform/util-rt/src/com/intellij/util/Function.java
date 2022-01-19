// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

/**
 * Deprecated. Use {@link java.util.function.Function} instead.
 */
public interface Function<Param, Result> {
  Result fun(Param param);

  interface Mono<T> extends Function<T, T> {}
}
