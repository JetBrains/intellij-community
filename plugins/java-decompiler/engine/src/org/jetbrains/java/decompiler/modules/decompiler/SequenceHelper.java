/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class SequenceHelper {


  public static void condenseSequences(Statement root) {
    condenseSequencesRec(root);
  }

  private static void condenseSequencesRec(Statement stat) {

    if (stat.type == Statement.TYPE_SEQUENCE) {

      List<Statement> lst = new ArrayList<>();
      lst.addAll(stat.getStats());

      boolean unfolded = false;

      // unfold blocks
      for (int i = 0; i < lst.size(); i++) {
        Statement st = lst.get(i);
        if (st.type == Statement.TYPE_SEQUENCE) {

          removeEmptyStatements((SequenceStatement)st);

          if (i == lst.size() - 1 || isSequenceDisbandable(st, lst.get(i + 1))) {
            // move predecessors
            Statement first = st.getFirst();
            for (StatEdge edge : st.getAllPredecessorEdges()) {
              st.removePredecessor(edge);
              edge.getSource().changeEdgeNode(Statement.DIRECTION_FORWARD, edge, first);
              first.addPredecessor(edge);
            }

            // move successors
            Statement last = st.getStats().getLast();
            if (last.getAllSuccessorEdges().isEmpty() && i < lst.size() - 1) {
              last.addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR, last, lst.get(i + 1)));
            }
            else {
              for (StatEdge edge : last.getAllSuccessorEdges()) {
                if (i == lst.size() - 1) {
                  if (edge.closure == st) {
                    stat.addLabeledEdge(edge);
                  }
                }
                else {
                  edge.getSource().changeEdgeType(Statement.DIRECTION_FORWARD, edge, StatEdge.TYPE_REGULAR);
                  edge.closure.getLabelEdges().remove(edge);
                  edge.closure = null;
                }
              }
            }

            for (StatEdge edge : st.getAllSuccessorEdges()) {
              st.removeSuccessor(edge);
            }

            for (StatEdge edge : new HashSet<>(st.getLabelEdges())) {
              if (edge.getSource() != last) {
                last.addLabeledEdge(edge);
              }
            }

            lst.remove(i);
            lst.addAll(i, st.getStats());
            i--;

            unfolded = true;
          }
        }
      }

      if (unfolded) {
        SequenceStatement sequence = new SequenceStatement(lst);
        sequence.setAllParent();

        stat.getParent().replaceStatement(stat, sequence);

        stat = sequence;
      }
    }

    // sequence consisting of one statement -> disband
    if (stat.type == Statement.TYPE_SEQUENCE) {

      removeEmptyStatements((SequenceStatement)stat);

      if (stat.getStats().size() == 1) {

        Statement st = stat.getFirst();

        boolean ok = st.getAllSuccessorEdges().isEmpty();
        if (!ok) {
          StatEdge edge = st.getAllSuccessorEdges().get(0);

          ok = stat.getAllSuccessorEdges().isEmpty();
          if (!ok) {
            StatEdge statedge = stat.getAllSuccessorEdges().get(0);
            ok = (edge.getDestination() == statedge.getDestination());

            if (ok) {
              st.removeSuccessor(edge);
            }
          }
        }

        if (ok) {
          stat.getParent().replaceStatement(stat, st);
          stat = st;
        }
      }
    }

    // replace flat statements with synthetic basic blocks
    outer:
    while (true) {
      for (Statement st : stat.getStats()) {
        if ((st.getStats().isEmpty() || st.getExprents() != null) && st.type != Statement.TYPE_BASICBLOCK) {
          destroyAndFlattenStatement(st);
          continue outer;
        }
      }
      break;
    }

    // recursion
    for (int i = 0; i < stat.getStats().size(); i++) {
      condenseSequencesRec(stat.getStats().get(i));
    }
  }

  private static boolean isSequenceDisbandable(Statement block, Statement next) {

    Statement last = block.getStats().getLast();
    List<StatEdge> lstSuccs = last.getAllSuccessorEdges();
    if (!lstSuccs.isEmpty()) {
      if (lstSuccs.get(0).getDestination() != next) {
        return false;
      }
    }

    for (StatEdge edge : next.getPredecessorEdges(StatEdge.TYPE_BREAK)) {
      if (last != edge.getSource() && !last.containsStatementStrict(edge.getSource())) {
        return false;
      }
    }

    return true;
  }

  private static void removeEmptyStatements(SequenceStatement sequence) {

    if (sequence.getStats().size() <= 1) {
      return;
    }

    mergeFlatStatements(sequence);

    while (true) {

      boolean found = false;

      for (Statement st : sequence.getStats()) {

        if (st.getExprents() != null && st.getExprents().isEmpty()) {

          if (st.getAllSuccessorEdges().isEmpty()) {
            List<StatEdge> lstBreaks = st.getPredecessorEdges(StatEdge.TYPE_BREAK);

            if (lstBreaks.isEmpty()) {
              for (StatEdge edge : st.getAllPredecessorEdges()) {
                edge.getSource().removeSuccessor(edge);
              }
              found = true;
            }
          }
          else {
            StatEdge sucedge = st.getAllSuccessorEdges().get(0);
            if (sucedge.getType() != StatEdge.TYPE_FINALLYEXIT) {
              st.removeSuccessor(sucedge);

              for (StatEdge edge : st.getAllPredecessorEdges()) {
                if (sucedge.getType() != StatEdge.TYPE_REGULAR) {
                  edge.getSource().changeEdgeType(Statement.DIRECTION_FORWARD, edge, sucedge.getType());
                }

                st.removePredecessor(edge);
                edge.getSource().changeEdgeNode(Statement.DIRECTION_FORWARD, edge, sucedge.getDestination());
                sucedge.getDestination().addPredecessor(edge);

                if (sucedge.closure != null) {
                  sucedge.closure.addLabeledEdge(edge);
                }
              }
              found = true;
            }
          }

          if (found) {
            sequence.getStats().removeWithKey(st.id);
            break;
          }
        }
      }

      if (!found) {
        break;
      }
    }

    sequence.setFirst(sequence.getStats().get(0));
  }

  private static void mergeFlatStatements(SequenceStatement sequence) {

    while (true) {

      Statement next;
      Statement current = null;

      boolean found = false;

      for (int i = sequence.getStats().size() - 1; i >= 0; i--) {

        next = current;
        current = sequence.getStats().get(i);

        if (next != null && current.getExprents() != null && !current.getExprents().isEmpty()) {
          if (next.getExprents() != null) {
            next.getExprents().addAll(0, current.getExprents());
            current.getExprents().clear();
            found = true;
          }
          else {
            Statement first = getFirstExprentlist(next);
            if (first != null) {
              first.getExprents().addAll(0, current.getExprents());
              current.getExprents().clear();
              found = true;
            }
          }
        }
      }

      if (!found) {
        break;
      }
    }
  }

  private static Statement getFirstExprentlist(Statement stat) {

    if (stat.getExprents() != null) {
      return stat;
    }

    switch (stat.type) {
      case Statement.TYPE_IF:
      case Statement.TYPE_SEQUENCE:
      case Statement.TYPE_SWITCH:
      case Statement.TYPE_SYNCRONIZED:
        return getFirstExprentlist(stat.getFirst());
    }

    return null;
  }


  public static void destroyAndFlattenStatement(Statement stat) {

    destroyStatementContent(stat, false);

    BasicBlockStatement bstat = new BasicBlockStatement(new BasicBlock(
      DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
    if (stat.getExprents() == null) {
      bstat.setExprents(new ArrayList<>());
    }
    else {
      bstat.setExprents(DecHelper.copyExprentList(stat.getExprents()));
    }

    stat.getParent().replaceStatement(stat, bstat);
  }

  public static void destroyStatementContent(Statement stat, boolean self) {

    for (Statement st : stat.getStats()) {
      destroyStatementContent(st, true);
    }
    stat.getStats().clear();

    if (self) {
      for (StatEdge edge : stat.getAllSuccessorEdges()) {
        stat.removeSuccessor(edge);
      }
    }
  }
}
