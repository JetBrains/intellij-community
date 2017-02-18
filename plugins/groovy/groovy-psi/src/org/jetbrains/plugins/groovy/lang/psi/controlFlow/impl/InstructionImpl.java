/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallEnvironment;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.NegatingGotoInstruction;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;

/**
 * @author ven
 */
public class InstructionImpl implements Instruction {
  private final LinkedHashSet<InstructionImpl> myPredecessors = new LinkedHashSet<>(1);
  private final LinkedHashSet<InstructionImpl> mySuccessors = new LinkedHashSet<>(1);
  private LinkedHashSet<NegatingGotoInstruction> myNegations;

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

  @Override
  public Iterable<? extends Instruction> successors(CallEnvironment environment) {
    final Deque<CallInstruction> stack = environment.callStack(this);
    for (InstructionImpl instruction : mySuccessors) {
      environment.update(stack, instruction);
    }

    return mySuccessors;
  }

  @Override
  public Iterable<? extends Instruction> predecessors(CallEnvironment environment) {
    final Deque<CallInstruction> stack = environment.callStack(this);
    for (InstructionImpl instruction : myPredecessors) {
      environment.update(stack, instruction);
    }

    return myPredecessors;
  }

  @Override
  public Iterable<? extends Instruction> allSuccessors() {
    return mySuccessors;
  }

  @Override
  public Iterable<? extends Instruction> allPredecessors() {
    return myPredecessors;
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(myNumber);
    builder.append("(");
    for (InstructionImpl successor : mySuccessors) {
      builder.append(successor.myNumber);
      builder.append(',');
    }
    if (!mySuccessors.isEmpty()) builder.delete(builder.length() - 1, builder.length());
    builder.append(") ").append(getElementPresentation());
    return builder.toString();
  }

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
      myNegations = new LinkedHashSet<>(1);
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
