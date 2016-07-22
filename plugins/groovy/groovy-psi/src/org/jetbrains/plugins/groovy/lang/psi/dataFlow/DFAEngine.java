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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import com.intellij.codeInspection.dataFlow.WorkingTimeMeasurer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallEnvironment;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowBuilderUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.*;

/**
 * @author ven
 */
public class DFAEngine<E> {

  private final Instruction[] myFlow;

  private final DfaInstance<E> myDfa;
  private final Semilattice<E> mySemilattice;

  public DFAEngine(Instruction[] flow, DfaInstance<E> dfa, Semilattice<E> semilattice) {
    myFlow = flow;
    myDfa = dfa;
    mySemilattice = semilattice;
  }

  private static class MyCallEnvironment implements CallEnvironment {
    ArrayList<Deque<CallInstruction>> myEnv;

    private MyCallEnvironment(int instructionNum) {
      myEnv = new ArrayList<>(instructionNum);
      for (int i = 0; i < instructionNum; i++) {
        myEnv.add(new ArrayDeque<>());
      }
    }

    @Override
    public Deque<CallInstruction> callStack(Instruction instruction) {
      return myEnv.get(instruction.num());
    }

    @Override
    public void update(Deque<CallInstruction> callStack, Instruction instruction) {
      myEnv.set(instruction.num(), callStack);
    }
  }

  @NotNull
  public ArrayList<E> performForceDFA() {
    ArrayList<E> result = performDFA(false);
    assert result != null;
    return result;
  }

  @Nullable
  public ArrayList<E> performDFAWithTimeout() {
    return performDFA(true);
  }

  @Nullable
  private ArrayList<E> performDFA(boolean timeout) {
    WorkingTimeMeasurer measurer = null;

    ArrayList<E> info = new ArrayList<>(Collections.nCopies(myFlow.length, myDfa.initial()));
    CallEnvironment env = new MyCallEnvironment(myFlow.length);
    
    boolean[] visited = new boolean[myFlow.length];

    final boolean forward = myDfa.isForward();
    int[] order = ControlFlowBuilderUtil.postorder(myFlow); //todo for backward?
    int count = 0;
    for (int i = forward ? 0 : myFlow.length - 1; forward ? i < myFlow.length : i >= 0;) {
      Instruction instr = myFlow[order[i]];

      if (!visited[instr.num()]) {
        Queue<Instruction> workList = new LinkedList<>();

        workList.add(instr);
        visited[instr.num()] = true;

        while (!workList.isEmpty()) {
          count++;
          if (timeout && count % 512 == 0) {
            if (measurer == null) {
              long msLimit = Registry.intValue("ide.dfa.time.limit.online");

              measurer = new WorkingTimeMeasurer(msLimit * 1000 * 1000);
            }
            else if (measurer.isTimeOver()) {
              return null;
            }
          }

          ProgressManager.checkCanceled();
          final Instruction curr = workList.remove();
          final int num = curr.num();
          final E oldE = info.get(num);
          E newE = join(curr, info, env);
          myDfa.fun(newE, curr);
          if (!mySemilattice.eq(newE, oldE)) {
            info.set(num, newE);
            for (Instruction next : getNext(curr, env)) {
              workList.add(next);
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
    final Iterable<? extends Instruction> prev = myDfa.isForward() ? instruction.predecessors(env) : instruction.successors(env);
    ArrayList<E> prevInfos = new ArrayList<>();
    for (Instruction i : prev) {
      prevInfos.add(info.get(i.num()));
    }
    return mySemilattice.join(prevInfos);
  }

  private Iterable<? extends Instruction> getNext(Instruction curr, CallEnvironment env) {
    return myDfa.isForward() ? curr.successors(env) : curr.predecessors(env);
  }
}
