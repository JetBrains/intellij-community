package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

/**
 * @author ven
 */
public interface Instruction {
  Iterable<Instruction> succ();
  Iterable<Instruction> pred();
}
