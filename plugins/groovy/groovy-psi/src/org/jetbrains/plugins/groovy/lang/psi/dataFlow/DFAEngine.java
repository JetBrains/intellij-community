// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
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
  public List<@Nullable E> performForceDFA() {
    List<E> result = performDFA(false);
    assert result != null;
    return result;
  }

  @Nullable
  public List<@Nullable E> performDFAWithTimeout() {
    return performDFA(true);
  }

  @Nullable
  private List<@Nullable E> performDFA(boolean timeout) {
    final int n = myFlow.length;
    final List<Optional<E>> info = getEmptyInfo(n);
    final CallEnvironment env = new MyCallEnvironment(n);

    final WorkList workList = new WorkList(n, getFlowOrder());

    while (!workList.isEmpty()) {
      ProgressManager.checkCanceled();
      if (timeout && checkCounter()) return null;
      final int num = workList.next();
      final Instruction curr = myFlow[num];
      final Optional<E> oldE = info.get(num);                        // saved outbound state
      final List<E> ins = getPrevInfos(curr, info, env);             // states from all inbound edges
      final E jointE = mySemilattice.join(ins);                      // inbound state
      final E newE = myDfa.fun(jointE, curr);                        // new outbound state
      if (oldE.isEmpty() || !mySemilattice.eq(newE, oldE.get())) {   // if outbound state changed
        info.set(num, Optional.of(newE));                            // save new state
        for (Instruction next : getNext(curr, env)) {
          workList.offer(next.num());
        }
      }
    }
    return ContainerUtil.map(info, e -> e.orElse(null));
  }

  @NotNull
  private List<Optional<E>> getEmptyInfo(int n) {
    //noinspection unchecked
    Optional<E>[] optionals = new Optional[n];
    Arrays.fill(optionals, Optional.empty());
    return Arrays.asList(optionals);
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
  private List<E> getPrevInfos(@NotNull Instruction instruction, @NotNull List<Optional<E>> info, @NotNull CallEnvironment env) {
    List<E> prevInfos = new SmartList<>();
    for (Instruction i : getPrevious(instruction, env)) {
      Optional<E> prevInfo = info.get(i.num());
      prevInfo.ifPresent(e -> prevInfos.add(e));
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
