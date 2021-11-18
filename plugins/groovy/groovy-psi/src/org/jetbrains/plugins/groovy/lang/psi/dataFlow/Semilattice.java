// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Since DFA in Groovy is immutable, it relies heavily on referential equality. Please note this to make more performant algorithm.
 */
public interface Semilattice<E> {

  /**
   * Neutral element of the semilattice. It must obey the law of left and right identity cancellation.
   */
  @NotNull
  E initial();

  @NotNull
  E join(@NotNull List<? extends E> ins);

  default boolean eq(@NotNull E e1, @NotNull E e2) {
    return e1 == e2 || e1.equals(e2);
  }
}
