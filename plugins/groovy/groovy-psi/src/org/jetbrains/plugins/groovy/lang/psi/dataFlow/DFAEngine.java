/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressManager;
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

  private WorkCounter myCounter = null;

  public DFAEngine(@NotNull Instruction[] flow, @NotNull DfaInstance<E> dfa, @NotNull Semilattice<E> semilattice) {
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

    @NotNull
    @Override
    public Deque<CallInstruction> callStack(@NotNull Instruction instruction) {
      return myEnv.get(instruction.num());
    }

    @Override
    public void update(@NotNull Deque<CallInstruction> callStack, @NotNull Instruction instruction) {
      myEnv.set(instruction.num(), callStack);
    }
  }

  @NotNull
  public List<E> performForceDFA() {
    List<E> result = performDFA(false);
    assert result != null;
    return result;
  }

  @Nullable
  public List<E> performDFAWithTimeout() {
    return performDFA(true);
  }

  @Nullable
  private List<E> performDFA(boolean timeout) {
    final int n = myFlow.length;
    final List<E> info = new ArrayList<>(Collections.nCopies(n, myDfa.initial()));
    final CallEnvironment env = new MyCallEnvironment(n);

    final WorkList workList = new WorkList(n);

    final int[] flowOrder = getFlowOrder();
    for (int i : flowOrder) {
      if (!workList.offer(myFlow[i])) continue;

      while (!workList.isEmpty()) {
        ProgressManager.checkCanceled();
        if (timeout && checkCounter()) return null;
        final Instruction curr = workList.remove();
        final int num = curr.num();
        final E oldE = info.get(num);                     // saved outbound state
        final E newE = getInboundState(curr, info, env);  // inbound state
        myDfa.fun(newE, curr);                            // newly modified outbound state
        if (!mySemilattice.eq(newE, oldE)) {              // if outbound state changed
          info.set(num, newE);                            // save new state
          for (Instruction next : getNext(curr, env)) {
            workList.offerUnconditionally(next);
          }
        }
      }
    }

    return info;
  }

  @NotNull
  private int[] getFlowOrder() {
    if (myDfa.isForward()) {
      return ControlFlowBuilderUtil.reversePostorder(myFlow);
    }
    else {
      return ControlFlowBuilderUtil.postorder(myFlow);
    }
  }

  @NotNull
  private E getInboundState(@NotNull Instruction instruction, @NotNull List<E> info, @NotNull CallEnvironment env) {
    List<E> prevInfos = new ArrayList<>();
    for (Instruction i : getPrevious(instruction, env)) {
      prevInfos.add(info.get(i.num()));
    }
    return mySemilattice.join(prevInfos);
  }

  @NotNull
  private Iterable<Instruction> getPrevious(@NotNull Instruction instruction, @NotNull CallEnvironment env) {
    return myDfa.isForward() ? instruction.predecessors(env) : instruction.successors(env);
  }

  @NotNull
  private Iterable<Instruction> getNext(@NotNull Instruction instruction, @NotNull CallEnvironment env) {
    return myDfa.isForward() ? instruction.successors(env) : instruction.predecessors(env);
  }

  private boolean checkCounter() {
    if (myCounter == null) {
      myCounter = new WorkCounter();
      return false;
    }
    return myCounter.isTimeOver();
  }
}
