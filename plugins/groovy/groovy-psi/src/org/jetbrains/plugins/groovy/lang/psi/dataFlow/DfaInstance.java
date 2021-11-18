// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * Data flow analysis engine for Groovy.
 * DFA is performed on a control flow graph consisting of {@link Instruction}.
 * <br>
 * Note, that DFA is immutable in its nature: elements of type {@code E} <b>should not be modified</b>.
 */
public interface DfaInstance<E> {

  E fun(@NotNull @Unmodifiable E e, @NotNull Instruction instruction);

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
