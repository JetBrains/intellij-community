// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

/**
 * Consider using {@link java.util.function.BiPredicate} instead.
 */
@FunctionalInterface
public interface PairProcessor<S, T> {
  boolean process(S s, T t);

  static @NotNull <S,T> PairProcessor<S,T> alwaysFalse() {
    return (__, __1) -> false;
  }

  static @NotNull <S,T> PairProcessor<S,T> alwaysTrue() {
    return (__, __1) -> true;
  }
}
