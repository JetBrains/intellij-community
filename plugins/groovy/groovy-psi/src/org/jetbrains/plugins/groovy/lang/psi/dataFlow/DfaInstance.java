// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * @author ven
 */
public interface DfaInstance<E> {

  void fun(@NotNull E e, @NotNull Instruction instruction);

  @NotNull
  E initial();

  default boolean isForward() {
    return true;
  }

  /**
   * @return {@code true} if this instance expects only instructions reachable from root
   */
  default boolean isReachable() {
    return false;
  }
}
