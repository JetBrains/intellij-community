// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallEnvironment;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.NegatingGotoInstruction;

import java.util.Collections;
import java.util.Deque;
import java.util.Set;

/**
 * @author ven
 */
public class InstructionImpl implements Instruction {
  private final Set<Instruction> myPredecessors = new ObjectArraySet<>(1);
  private final Set<Instruction> mySuccessors = new ObjectArraySet<>(1);
  private Set<NegatingGotoInstruction> myNegations;

  protected final PsiElement myPsiElement;
  private int myNumber = -1;

  @Override
  @Nullable
  public PsiElement getElement() {
    return myPsiElement;
  }

  public InstructionImpl(@Nullable PsiElement element) {
    myPsiElement = element;
  }

  @NotNull
  @Override
  public Iterable<Instruction> successors(@NotNull CallEnvironment environment) {
    final Deque<CallInstruction> stack = environment.callStack(this);
    for (Instruction instruction : mySuccessors) {
      environment.update(stack, instruction);
    }
    return mySuccessors;
  }

  @NotNull
  @Override
  public Iterable<Instruction> predecessors(@NotNull CallEnvironment environment) {
    final Deque<CallInstruction> stack = environment.callStack(this);
    for (Instruction instruction : myPredecessors) {
      environment.update(stack, instruction);
    }
    return myPredecessors;
  }

  @NotNull
  @Override
  public Iterable<Instruction> allSuccessors() {
    return mySuccessors;
  }

  @NotNull
  @Override
  public Iterable<Instruction> allPredecessors() {
    return myPredecessors;
  }

  @NonNls
  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(myNumber);
    builder.append("(");
    for (Instruction successor : mySuccessors) {
      builder.append(successor.num());
      builder.append(',');
    }
    if (!mySuccessors.isEmpty()) builder.delete(builder.length() - 1, builder.length());
    builder.append(") ").append(getElementPresentation());
    return builder.toString();
  }

  @NotNull
  @NonNls
  protected String getElementPresentation() {
    //return "element: " + (myPsiElement != null ? myPsiElement.getText() : null);
    return "element: " + myPsiElement;
  }

  @Override
  public int num() {
    assert myNumber != -1;
    return myNumber;
  }

  @NotNull
  @Override
  public Iterable<? extends NegatingGotoInstruction> getNegatingGotoInstruction() {
    if (myNegations == null) {
      return Collections.emptyList();
    }
    return myNegations;
  }

  public void addSuccessor(InstructionImpl instruction) {
    mySuccessors.add(instruction);
  }

  public void addPredecessor(InstructionImpl instruction) {
    myPredecessors.add(instruction);
  }

  void addNegationsFrom(Instruction instruction) {
    if (myNegations == null) {
      myNegations = new ObjectArraySet<>(1);
    }
    for (NegatingGotoInstruction negation : instruction.getNegatingGotoInstruction()) {
      myNegations.add(negation);
    }
    if (instruction instanceof NegatingGotoInstruction) {
      myNegations.add((NegatingGotoInstruction)instruction);
    }
  }

  final void setNumber(int num) {
    assert myNumber == -1;
    myNumber = num;
  }
}
