package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;

/**
 * @author ven
 */
class InstructionImpl implements Instruction, Cloneable {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl");

  ArrayList<InstructionImpl> myPred = new ArrayList<InstructionImpl>();

  ArrayList<InstructionImpl> mySucc = new ArrayList<InstructionImpl>();

  PsiElement myPsiElement;
  private int myNumber;

  @Nullable
  public PsiElement getElement() {
    return myPsiElement;
  }

  InstructionImpl(PsiElement element, int num) {
    myPsiElement = element;
    myNumber = num;
  }

  protected Stack<CallInstruction> getStack(ArrayList<Stack<CallInstruction>> env, InstructionImpl instruction) {
    return env.get(instruction.num() - 1);
  }

  public Iterable<? extends Instruction> succ(ArrayList<Stack<CallInstruction>> env) {
    final Stack<CallInstruction> stack = getStack(env, this);
    for (InstructionImpl instruction : mySucc) {
      env.set(instruction.num() - 1, stack);
    }

    return mySucc;
  }

  public Iterable<? extends Instruction> pred(ArrayList<Stack<CallInstruction>> env) {
    final Stack<CallInstruction> stack = getStack(env, this);
    for (InstructionImpl instruction : myPred) {
      env.set(instruction.num() - 1, stack);
    }

    if (myPred.size() > 0) {
      return myPred;
    }

    Stack<CallInstruction> myCallStack = env.get(num() - 1);
    if (!myCallStack.isEmpty()) {
      final CallInstruction callInstruction = myCallStack.peek();
      return callInstruction.pred(env);
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
