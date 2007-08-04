package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import java.util.Stack;

/**
 * @author ven
 */
public interface Instruction {
  Iterable<? extends Instruction> succ(Stack<CallInstruction> callStack);
  Iterable<? extends Instruction> pred();
}
