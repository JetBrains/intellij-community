// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

public class ReturnInstruction extends InstructionImpl {
  public ReturnInstruction(GrFinallyClause finallyClause) {
    super(finallyClause);
  }

  @Override
  public String toString() {
    return super.toString() + " RETURN";
  }

  @Override
  protected @NotNull String getElementPresentation() {
    return "";
  }

  @Override
  public @NotNull Iterable<Instruction> successors(@NotNull CallEnvironment environment) {
    final Deque<CallInstruction> callStack = environment.callStack(this);
    if (callStack.isEmpty()) return Collections.emptyList();     //can be true in case env was not populated (e.g. by DFA)

    final CallInstruction callInstruction = callStack.peek();
    final Iterable<Instruction> successors = callInstruction.allSuccessors();
    final Deque<CallInstruction> copy = new ArrayDeque<>(callStack);
    copy.pop();
    for (Instruction instruction : successors) {
      environment.update(copy, instruction);
    }

    return successors;
  }
}
