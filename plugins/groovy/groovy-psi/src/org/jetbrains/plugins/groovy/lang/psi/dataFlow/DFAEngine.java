// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallEnvironment;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.OrderUtil.postOrder;
import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.OrderUtil.reversedPostOrder;

/**
 * @author ven
 */
public final class DFAEngine<E> {
  private final Instruction[] myFlow;
  private final DfaInstance<E> myDfa;
  private final Semilattice<E> mySemilattice;

  private WorkCounter myCounter = null;

  public DFAEngine(Instruction @NotNull [] flow, @NotNull DfaInstance<E> dfa, @NotNull Semilattice<E> semilattice) {
    myFlow = flow;
    myDfa = dfa;
    mySemilattice = semilattice;
  }

  private static final class MyCallEnvironment implements CallEnvironment {
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
    final List<E> info = new ArrayList<>(Collections.nCopies(n, mySemilattice.initial()));
    final CallEnvironment env = new MyCallEnvironment(n);

    final WorkList workList = new WorkList(n, getFlowOrder());

    while (!workList.isEmpty()) {
      ProgressManager.checkCanceled();
      if (timeout && checkCounter()) return null;
      final int num = workList.next();
      final Instruction curr = myFlow[num];
      final E oldE = info.get(num);                      // saved outbound state
      final List<E> ins = getPrevInfos(curr, info, env); // states from all inbound edges
      final E jointE = join(ins);                        // inbound state
      final E newE = myDfa.fun(jointE, curr);            // newly modified outbound state
      if (!mySemilattice.eq(newE, oldE)) {               // if outbound state changed
        info.set(num, newE);                             // save new state
        for (Instruction next : getNext(curr, env)) {
          workList.offer(next.num());
        }
      }
    }

    return info;
  }

  private E join(List<? extends E> states) {
    if (states.size() == 0) {
      return mySemilattice.initial();
    }
    if (states.size() == 1) {
      return states.get(0);
    }
    return mySemilattice.join(states);
  }

  private int @NotNull [] getFlowOrder() {
    if (myDfa.isForward()) {
      return reversedPostOrder(myFlow, myDfa.isReachable());
    }
    else {
      return postOrder(myFlow, myDfa.isReachable());
    }
  }

  @NotNull
  private List<E> getPrevInfos(@NotNull Instruction instruction, @NotNull List<E> info, @NotNull CallEnvironment env) {
    List<E> prevInfos = new ArrayList<>();
    for (Instruction i : getPrevious(instruction, env)) {
      if (info.get(i.num()) != mySemilattice.initial()) {
        prevInfos.add(info.get(i.num()));
      }
    }
    return prevInfos;
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
