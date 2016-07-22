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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

/**
* @author peter
*/
public class ReturnInstruction extends InstructionImpl {
  public ReturnInstruction(GrFinallyClause finallyClause) {
    super(finallyClause);
  }

  public String toString() {
    return super.toString() + " RETURN";
  }

  @Override
  protected String getElementPresentation() {
    return "";
  }

  @Override
  public Iterable<? extends Instruction> successors(CallEnvironment environment) {
    final Deque<CallInstruction> callStack = environment.callStack(this);
    if (callStack.isEmpty()) return Collections.emptyList();     //can be true in case env was not populated (e.g. by DFA)

    final CallInstruction callInstruction = callStack.peek();
    final Iterable<? extends Instruction> successors = callInstruction.allSuccessors();
    final Deque<CallInstruction> copy = new ArrayDeque<>(callStack);
    copy.pop();
    for (Instruction instruction : successors) {
      environment.update(copy, instruction);
    }

    return successors;
  }
}
