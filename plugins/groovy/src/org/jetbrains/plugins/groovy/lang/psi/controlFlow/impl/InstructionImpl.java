package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallInstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.Collections;

/**
 * @author ven
 */
class InstructionImpl implements Instruction {
  List<InstructionImpl> myPred = new ArrayList<InstructionImpl>();

  List<InstructionImpl> mySucc = new ArrayList<InstructionImpl>();

  PsiElement myPsiElement;
  private int myNumber;

  @Nullable
  public PsiElement getElement() {
    return myPsiElement;
  }

  InstructionImpl(PsiElement psiElement, int num) {
    myPsiElement = psiElement;
    myNumber = num;
  }

  public Iterable<? extends Instruction> succ(Stack<CallInstruction> callStack) {
    return mySucc;
  }

  public Iterable<? extends Instruction> pred(Stack<CallInstruction> callStack) {
    if (myPred.size() > 0) {
      return myPred;
    }

    if (!callStack.isEmpty()) {
      return callStack.pop().pred(callStack);
    }

    return Collections.emptyList();
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(myNumber);
    builder.append("(");
    for (int i = 0; i < mySucc.size(); i++) {
      if (i > 0) builder.append(',');
      builder.append(mySucc.get(i).myNumber);
    }
    builder.append(") ").append(getElementPresentation());
    return builder.toString();
  }

  protected String getElementPresentation() {
    return "element: " + myPsiElement;
  }

  public int num() {
    return myNumber;
  }
}
