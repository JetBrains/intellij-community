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
class InstructionImpl implements Instruction, Cloneable {
  List<InstructionImpl> myPred = new ArrayList<InstructionImpl>();

  List<InstructionImpl> mySucc = new ArrayList<InstructionImpl>();

  PsiElement myPsiElement;
  private int myNumber;

  private static final Stack<CallInstruction> ourEmptyCallStack = new Stack<CallInstruction>();
  protected Stack<CallInstruction> myCallStack = ourEmptyCallStack; //copy on write

  @Nullable
  public PsiElement getElement() {
    return myPsiElement;
  }

  InstructionImpl(PsiElement element, int num) {
    myPsiElement = element;
    myNumber = num;
  }

  protected InstructionImpl clone() {
    try {
      final InstructionImpl clone = (InstructionImpl) super.clone();
      clone.myCallStack = (Stack<CallInstruction>) myCallStack.clone();
      return clone;
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
      return null;
    }
  }

  public Iterable<? extends Instruction> succ() {
    for (InstructionImpl instruction : mySucc) {
      instruction.myCallStack = myCallStack;
    }
    
    return mySucc;
  }

  public Iterable<? extends Instruction> pred() {
    for (InstructionImpl instruction : myPred) {
      instruction.myCallStack = myCallStack;
    }

    if (myPred.size() > 0) {
      return myPred;
    }

    if (!myCallStack.isEmpty()) {
      final CallInstruction callInstruction = myCallStack.peek();
      return callInstruction.pred();
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
