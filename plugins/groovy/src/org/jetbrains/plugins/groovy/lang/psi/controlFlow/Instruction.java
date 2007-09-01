package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.Stack;
import java.util.ArrayList;

/**
 * @author ven
 */
public interface Instruction {
  Iterable<? extends Instruction> succ(ArrayList<Stack<CallInstruction>> env);
  Iterable<? extends Instruction> pred(ArrayList<Stack<CallInstruction>> env);

  int num();

  @Nullable
  PsiElement getElement();

}
