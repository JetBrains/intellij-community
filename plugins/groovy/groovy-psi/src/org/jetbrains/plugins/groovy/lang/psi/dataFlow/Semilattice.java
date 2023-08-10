// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Since DFA in Groovy is immutable, it relies heavily on referential equality. Please note this to make a more performant implementation.
 */
public interface Semilattice<E> {

  @NotNull
  E join(@NotNull List<? extends @NotNull E> ins);

  default boolean eq(@NotNull E e1, @NotNull E e2) {
    return e1 == e2 || e1.equals(e2);
  }
}
