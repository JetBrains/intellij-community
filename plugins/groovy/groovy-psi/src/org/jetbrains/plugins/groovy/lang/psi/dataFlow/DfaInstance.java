// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * Data flow analysis engine for Groovy.
 * DFA is performed on a control flow graph consisting of {@link Instruction}.
 */
public interface DfaInstance<E> {

  /**
   * Instances of {@link E} can occur anywhere, therefore they should be immutable.
   */
  E fun(@NotNull E e, @NotNull Instruction instruction);

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
