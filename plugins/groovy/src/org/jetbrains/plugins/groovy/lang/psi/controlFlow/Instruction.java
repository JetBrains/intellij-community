package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import org.jetbrains.annotations.Nullable;

import java.util.Stack;

import com.intellij.psi.PsiElement;

/**
 * @author ven
 */
public interface Instruction {
  Iterable<? extends Instruction> succ(Stack<CallInstruction> callStack);
  Iterable<? extends Instruction> pred(Stack<CallInstruction> callStack);

  int num();

  @Nullable
  PsiElement getElement();
}
