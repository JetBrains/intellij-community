/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
* @author peter
*/
public class RetInstruction extends InstructionImpl {
  public RetInstruction(int num) {
    super(null, num);
  }

  public String toString() {
    return super.toString() + " RETURN";
  }

  protected String getElementPresentation() {
    return "";
  }

  public Iterable<? extends Instruction> succ(CallEnvironment env) {
    final Stack<CallInstruction> callStack = getStack(env, this);
    if (callStack.isEmpty()) return Collections.emptyList();     //can be true in case env was not populated (e.g. by DFA)

    final CallInstruction callInstruction = callStack.peek();
    final List<InstructionImpl> succ = callInstruction.mySucc;
    final Stack<CallInstruction> copy = (Stack<CallInstruction>)callStack.clone();
    copy.pop();
    for (InstructionImpl instruction : succ) {
      env.update(copy, instruction);
    }

    return succ;
  }
}
