package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

/**
 * @author ven
 */
public class DFAEngine<E> {
  private Instruction[] myFlow;

  private DFA<E> myDfa;
  private Semilattice<E> mySemilattice;

  public DFAEngine(Instruction[] flow,
                   DFA<E> dfa,
                   Semilattice<E> semilattice
  ) {
    myFlow = flow;
    myDfa = dfa;
    mySemilattice = semilattice;
  }

  public ArrayList<E> performDFA() {
    ArrayList<E> info = new ArrayList<E>(myFlow.length);
    for (int i = 0; i < myFlow.length; i++) {
      info.set(i, myDfa.initial());
    }

    boolean[] visited = new boolean[myFlow.length];

    final boolean forward = myDfa.isForward();
    for (int i = forward ? 0 : myFlow.length - 1; forward ? i < myFlow.length : i >= 0;) {
      Instruction instr = myFlow[i];
      if (visited[instr.num()]) continue;

      Stack<CallInstruction> callStack = new Stack<CallInstruction>();

      Queue<Instruction> worklist = new LinkedList<Instruction>();

      worklist.add(instr);
      visited[instr.num()] = true;

      while (!worklist.isEmpty()) {
        final Instruction curr = worklist.element();
        final int num = curr.num();
        final E oldE = info.get(num);
        E newE = myDfa.fun(curr);
        if (newE == null) continue;

        if (oldE != null) newE = mySemilattice.cap(newE, oldE);

        if (oldE == null || !mySemilattice.eq(newE, oldE)) {
          info.set(num, newE);
          for (Instruction next : getNext(curr, callStack)) {
            worklist.add(next);
            visited[next.num()] = true;
          }
        }
      }

      assert callStack.isEmpty();

      if (forward) i++; else i--;
    }


    return info;
  }

  private Iterable<? extends Instruction> getNext(Instruction curr, Stack<CallInstruction> callStack) {
    return myDfa.isForward() ? curr.succ(callStack) : curr.pred(callStack);
  }
}
