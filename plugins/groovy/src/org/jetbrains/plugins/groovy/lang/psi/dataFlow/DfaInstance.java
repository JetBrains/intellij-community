package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * @author ven
 */
public interface DfaInstance<E> {
  void fun(E e, Instruction instruction);

  @NotNull
  E initial();

  boolean isForward();
}
