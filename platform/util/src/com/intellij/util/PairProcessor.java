// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
@FunctionalInterface
public interface PairProcessor<S, T> {
  boolean process(S s, T t);


  @NotNull
  static <S,T> PairProcessor<S,T> alwaysFalse() {
    return (__, __1) -> false;
  }

  @NotNull
  static <S,T> PairProcessor<S,T> alwaysTrue() {
    return (__, __1) -> true;
  }
}
