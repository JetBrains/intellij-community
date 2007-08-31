package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

/**
 * @author ven
 */
public class DFAEngine<E> {
  private Instruction[] myFlow;

  private DfaInstance<E> myDfa;
  private Semilattice<E> mySemilattice;

  public DFAEngine(Instruction[] flow,
                   DfaInstance<E> dfa,
                   Semilattice<E> semilattice
  ) {
    myFlow = flow;
    myDfa = dfa;
    mySemilattice = semilattice;
  }

  public ArrayList<E> performDFA() {
    ArrayList<E> info = new ArrayList<E>(myFlow.length);
    for (int i = 0; i < myFlow.length; i++) {
      info.add(myDfa.initial());
    }

    boolean[] visited = new boolean[myFlow.length];

    final boolean forward = myDfa.isForward();
    for (int i = forward ? 0 : myFlow.length - 1; forward ? i < myFlow.length : i >= 0;) {
      Instruction instr = myFlow[i];

      if (!visited[instr.num() - 1]) {
        Queue<Instruction> worklist = new LinkedList<Instruction>();

        worklist.add(instr);
        visited[instr.num() - 1] = true;

        while (!worklist.isEmpty()) {
          final Instruction curr = worklist.remove();
          final int num = curr.num() - 1;
          final E oldE = info.get(num);
          E newE = join(curr, info);
          myDfa.fun(newE, curr);
          if (!mySemilattice.eq(newE, oldE)) {
            info.set(num, newE);
            for (Instruction next : getNext(curr)) {
              worklist.add(next);
              visited[next.num() - 1] = true;
            }
          }
        }
      }

      if (forward) i++; else i--;
    }


    return info;
  }

  private E join(Instruction instruction, ArrayList<E> info) {
    final Iterable<? extends Instruction> prev = myDfa.isForward() ? instruction.pred() : instruction.succ();
    ArrayList<E> prevInfos = new ArrayList<E>();
    for (Instruction i : prev) {
      prevInfos.add(info.get(i.num() - 1));
    }
    return mySemilattice.join(prevInfos);
  }

  private Iterable<? extends Instruction> getNext(Instruction curr) {
    return myDfa.isForward() ? curr.succ() : curr.pred();
  }
}
