package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

/**
 * @author ven
 */
public interface Instruction {
  Iterable<? extends Instruction> succ();
  Iterable<? extends Instruction> pred();
}
