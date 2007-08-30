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
        Stack<CallInstruction> callStack = new Stack<CallInstruction>();

        Queue<Instruction> worklist = new LinkedList<Instruction>();

        worklist.add(instr);
        visited[instr.num() - 1] = true;

        while (!worklist.isEmpty()) {
          final Instruction curr = worklist.remove();
          final int num = curr.num() - 1;
          final E oldE = info.get(num);
          E newE = join(curr, info, callStack);
          myDfa.fun(newE, curr);
          if (!mySemilattice.eq(newE, oldE)) {
            info.set(num, newE);
            for (Instruction next : getNext(curr, callStack)) {
              worklist.add(next);
              visited[next.num() - 1] = true;
            }
          }
        }

        assert callStack.isEmpty();
      }

      if (forward) i++; else i--;
    }


    return info;
  }

  private E join(Instruction instruction, ArrayList<E> info, Stack<CallInstruction> callStack) {
    final Stack<CallInstruction> copy = (Stack<CallInstruction>) callStack.clone();  //enviroanment should not be modified in join
    final Iterable<? extends Instruction> prev = myDfa.isForward() ? instruction.pred(copy) : instruction.succ(copy);
    ArrayList<E> prevInfos = new ArrayList<E>();
    for (Instruction i : prev) {
      prevInfos.add(info.get(i.num() - 1));
    }
    return mySemilattice.join(prevInfos);
  }

  private Iterable<? extends Instruction> getNext(Instruction curr, Stack<CallInstruction> callStack) {
    return myDfa.isForward() ? curr.succ(callStack) : curr.pred(callStack);
  }
}
