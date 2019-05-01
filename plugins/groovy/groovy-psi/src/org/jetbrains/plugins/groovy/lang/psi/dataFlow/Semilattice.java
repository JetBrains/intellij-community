// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Semilattice<E> {

  @NotNull
  E initial();

  @NotNull
  E join(@NotNull List<? extends E> ins);

  default boolean eq(@NotNull E e1, @NotNull E e2) {
    return e1.equals(e2);
  }
}
