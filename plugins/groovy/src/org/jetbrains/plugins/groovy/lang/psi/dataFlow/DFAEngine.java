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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

/**
 * @author ven
 */
public class DFAEngine<E> {
  private final Instruction[] myFlow;

  private final DfaInstance<E> myDfa;
  private final Semilattice<E> mySemilattice;

  public DFAEngine(Instruction[] flow,
                   DfaInstance<E> dfa,
                   Semilattice<E> semilattice) {
    myFlow = flow;
    myDfa = dfa;
    mySemilattice = semilattice;
  }

  private static class MyCallEnvironment implements CallEnvironment {
    ArrayList<Stack<CallInstruction>> myEnv;

    private MyCallEnvironment(int instructionNum) {
      myEnv = new ArrayList<Stack<CallInstruction>>(instructionNum);
      for (int i = 0; i < instructionNum; i++) {
        myEnv.add(new Stack<CallInstruction>());
      }
    }

    public Stack<CallInstruction> callStack(Instruction instruction) {
      return myEnv.get(instruction.num());
    }

    public void update(Stack<CallInstruction> callStack, Instruction instruction) {
      myEnv.set(instruction.num(), callStack);
    }
  }

  public ArrayList<E> performDFA() {
    ArrayList<E> info = new ArrayList<E>(myFlow.length);
    CallEnvironment env = new MyCallEnvironment(myFlow.length);
    for (int i = 0; i < myFlow.length; i++) {
      info.add(myDfa.initial());
    }

    boolean[] visited = new boolean[myFlow.length];

    final boolean forward = myDfa.isForward();
    int[] order = ControlFlowBuilderUtil.postorder(myFlow); //todo for backward?
    for (int i = forward ? 0 : myFlow.length - 1; forward ? i < myFlow.length : i >= 0;) {
      Instruction instr = myFlow[order[i]];

      if (!visited[instr.num()]) {
        Queue<Instruction> worklist = new LinkedList<Instruction>();

        worklist.add(instr);
        visited[instr.num()] = true;

        while (!worklist.isEmpty()) {
          final Instruction curr = worklist.remove();
          final int num = curr.num();
          final E oldE = info.get(num);
          E newE = join(curr, info, env);
          myDfa.fun(newE, curr);
          if (!mySemilattice.eq(newE, oldE)) {
            info.set(num, newE);
            for (Instruction next : getNext(curr, env)) {
              worklist.add(next);
              visited[next.num()] = true;
            }
          }
        }
      }

      if (forward) i++;
      else i--;
    }


    return info;
  }

  private E join(Instruction instruction, ArrayList<E> info, CallEnvironment env) {
    final Iterable<? extends Instruction> prev = myDfa.isForward() ? instruction.pred(env) : instruction.succ(env);
    ArrayList<E> prevInfos = new ArrayList<E>();
    for (Instruction i : prev) {
      prevInfos.add(info.get(i.num()));
    }
    return mySemilattice.join(prevInfos);
  }

  private Iterable<? extends Instruction> getNext(Instruction curr, CallEnvironment env) {
    return myDfa.isForward() ? curr.succ(env) : curr.pred(env);
  }
}
