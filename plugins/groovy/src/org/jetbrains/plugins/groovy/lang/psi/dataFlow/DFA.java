package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * @author ven
 */
public interface DFA<E> {
  @Nullable
  E fun(Instruction instruction);

  @NotNull
  E initial();

  boolean isForward();
}
