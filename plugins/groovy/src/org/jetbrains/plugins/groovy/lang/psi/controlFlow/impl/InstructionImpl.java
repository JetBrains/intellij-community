/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallEnvironment;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.LinkedHashSet;
import java.util.Stack;

/**
 * @author ven
 */
public class InstructionImpl implements Instruction {
  private final LinkedHashSet<InstructionImpl> myPredecessors = new LinkedHashSet<InstructionImpl>();
  private final LinkedHashSet<InstructionImpl> mySuccessors = new LinkedHashSet<InstructionImpl>();

  PsiElement myPsiElement;
  private final int myNumber;

  @Nullable
  public PsiElement getElement() {
    return myPsiElement;
  }

  public InstructionImpl(@Nullable PsiElement element, int num) {
    myPsiElement = element;
    myNumber = num;
  }

  protected static Stack<CallInstruction> getStack(CallEnvironment env, InstructionImpl instruction) {
    return env.callStack(instruction);
  }

  public Iterable<? extends Instruction> successors(CallEnvironment environment) {
    final Stack<CallInstruction> stack = getStack(environment, this);
    for (InstructionImpl instruction : mySuccessors) {
      environment.update(stack, instruction);
    }

    return mySuccessors;
  }

  public Iterable<? extends Instruction> predecessors(CallEnvironment environment) {
    final Stack<CallInstruction> stack = getStack(environment, this);
    for (InstructionImpl instruction : myPredecessors) {
      environment.update(stack, instruction);
    }

    return myPredecessors;
  }

  public Iterable<? extends Instruction> allSuccessors() {
    return mySuccessors;
  }

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
    return "element: " + myPsiElement;
  }

  public int num() {
    return myNumber;
  }

  public void addSuccessor(InstructionImpl instruction) {
    mySuccessors.add(instruction);
  }

  public void addPredecessor(InstructionImpl instruction) {
    myPredecessors.add(instruction);
  }
}
