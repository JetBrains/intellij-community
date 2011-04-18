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

import java.util.ArrayList;
import java.util.Stack;

/**
 * @author ven
 */
public class InstructionImpl implements Instruction, Cloneable {
  ArrayList<InstructionImpl> myPred = new ArrayList<InstructionImpl>();

  ArrayList<InstructionImpl> mySucc = new ArrayList<InstructionImpl>();

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

  protected Stack<CallInstruction> getStack(CallEnvironment env, InstructionImpl instruction) {
    return env.callStack(instruction);
  }

  public Iterable<? extends Instruction> succ(CallEnvironment env) {
    final Stack<CallInstruction> stack = getStack(env, this);
    for (InstructionImpl instruction : mySucc) {
      env.update(stack, instruction);
    }

    return mySucc;
  }

  public Iterable<? extends Instruction> pred(CallEnvironment env) {
    final Stack<CallInstruction> stack = getStack(env, this);
    for (InstructionImpl instruction : myPred) {
      env.update(stack, instruction);
    }

    return myPred;
  }

  public Iterable<? extends Instruction> allSucc() {
    return mySucc;
  }

  public Iterable<? extends Instruction> allPred() {
    return myPred;
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
