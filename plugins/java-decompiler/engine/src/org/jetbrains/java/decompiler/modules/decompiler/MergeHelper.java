// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.IfExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement.LoopType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeDirection;

public final class MergeHelper {
  public static void enhanceLoops(Statement root) {
    while (enhanceLoopsRec(root)) /**/;
    SequenceHelper.condenseSequences(root);
  }

  private static boolean enhanceLoopsRec(Statement stat) {
    boolean res = false;

    for (Statement st : stat.getStats()) {
      if (st.getExprents() == null) {
        res |= enhanceLoopsRec(st);
      }
    }

    if (stat.type == StatementType.DO) {
      res |= enhanceLoop((DoStatement)stat);
    }

    return res;
  }

  private static boolean enhanceLoop(DoStatement stat) {
    LoopType oldLoop = stat.getLoopType();

    switch (oldLoop) {
      case DO:

        // identify a while loop
        if (matchWhile(stat)) {
          // identify a for loop - subtype of while
          matchFor(stat);
        }
        else {
          // identify a do{}while loop
          matchDoWhile(stat);
        }

        break;
      case WHILE:
        matchFor(stat);
    }

    return (stat.getLoopType() != oldLoop);
  }

  private static void matchDoWhile(DoStatement stat) {
    // search for an if condition at the end of the loop
    Statement last = stat.getFirst();
    while (last.type == StatementType.SEQUENCE) {
      last = last.getStats().getLast();
    }

    if (last.type == StatementType.IF) {
      IfStatement lastif = (IfStatement)last;
      if (lastif.iftype == IfStatement.IFTYPE_IF && lastif.getIfstat() == null) {
        StatEdge ifedge = lastif.getIfEdge();
        StatEdge elseedge = lastif.getAllSuccessorEdges().get(0);

        if ((ifedge.getType() == EdgeType.BREAK && elseedge.getType() == EdgeType.CONTINUE && elseedge.closure == stat
             && isDirectPath(stat, ifedge.getDestination())) ||
            (ifedge.getType() == EdgeType.CONTINUE && elseedge.getType() == EdgeType.BREAK && ifedge.closure == stat
             && isDirectPath(stat, elseedge.getDestination()))) {

          Set<Statement> set = stat.getNeighboursSet(EdgeType.CONTINUE, EdgeDirection.BACKWARD);
          set.remove(last);

          if (!set.isEmpty()) {
            return;
          }

          stat.setLoopType(LoopType.DO_WHILE);

          IfExprent ifexpr = (IfExprent)lastif.getHeadexprent().copy();
          if (ifedge.getType() == EdgeType.BREAK) {
            ifexpr.negateIf();
          }
          stat.setConditionExprent(ifexpr.getCondition());
          lastif.getFirst().removeSuccessor(ifedge);
          lastif.removeSuccessor(elseedge);

          // remove empty if
          if (lastif.getFirst().getExprents().isEmpty()) {
            removeLastEmptyStatement(stat, lastif);
          }
          else {
            lastif.setExprents(lastif.getFirst().getExprents());

            StatEdge newedge = new StatEdge(EdgeType.CONTINUE, lastif, stat);
            lastif.addSuccessor(newedge);
            stat.addLabeledEdge(newedge);
          }

          if (stat.getAllSuccessorEdges().isEmpty()) {
            StatEdge edge = elseedge.getType() == EdgeType.CONTINUE ? ifedge : elseedge;

            edge.setSource(stat);
            if (edge.closure == stat) {
              edge.closure = stat.getParent();
            }
            stat.addSuccessor(edge);
          }
        }
      }
    }
  }

  private static boolean matchWhile(DoStatement stat) {

    // search for an if condition at the entrance of the loop
    Statement first = stat.getFirst();
    while (first.type == StatementType.SEQUENCE) {
      first = first.getFirst();
    }

    // found an if statement
    if (first.type == StatementType.IF) {
      IfStatement firstif = (IfStatement)first;

      if (firstif.getFirst().getExprents().isEmpty()) {

        if (firstif.iftype == IfStatement.IFTYPE_IF) {
          if (firstif.getIfstat() == null) {
            StatEdge ifedge = firstif.getIfEdge();
            if (isDirectPath(stat, ifedge.getDestination())) {
              // exit condition identified
              stat.setLoopType(LoopType.WHILE);

              // negate condition (while header)
              IfExprent ifexpr = (IfExprent)firstif.getHeadexprent().copy();
              ifexpr.negateIf();
              stat.setConditionExprent(ifexpr.getCondition());

              // remove edges
              firstif.getFirst().removeSuccessor(ifedge);
              firstif.removeSuccessor(firstif.getAllSuccessorEdges().get(0));

              if (stat.getAllSuccessorEdges().isEmpty()) {
                ifedge.setSource(stat);
                if (ifedge.closure == stat) {
                  ifedge.closure = stat.getParent();
                }
                stat.addSuccessor(ifedge);
              }

              // remove empty if statement as it is now part of the loop
              if (firstif == stat.getFirst()) {
                BasicBlockStatement bstat = new BasicBlockStatement(new BasicBlock(
                  DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
                bstat.setExprents(new ArrayList<>());
                stat.replaceStatement(firstif, bstat);
              }
              else {
                // precondition: sequence must contain more than one statement!
                Statement sequence = firstif.getParent();
                sequence.getStats().removeWithKey(firstif.id);
                sequence.setFirst(sequence.getStats().get(0));
              }

              return true;
            }
          }
          else {
            StatEdge elseedge = firstif.getAllSuccessorEdges().get(0);
            if (isDirectPath(stat, elseedge.getDestination())) {
              // exit condition identified
              stat.setLoopType(LoopType.WHILE);

              // no need to negate the while condition
              stat.setConditionExprent(((IfExprent)firstif.getHeadexprent().copy()).getCondition());

              // remove edges
              StatEdge ifedge = firstif.getIfEdge();
              firstif.getFirst().removeSuccessor(ifedge);
              firstif.removeSuccessor(elseedge);

              if (stat.getAllSuccessorEdges().isEmpty()) {

                elseedge.setSource(stat);
                if (elseedge.closure == stat) {
                  elseedge.closure = stat.getParent();
                }
                stat.addSuccessor(elseedge);
              }

              if (firstif.getIfstat() == null) {
                BasicBlockStatement bstat = new BasicBlockStatement(new BasicBlock(
                  DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
                bstat.setExprents(new ArrayList<>());

                ifedge.setSource(bstat);
                bstat.addSuccessor(ifedge);

                stat.replaceStatement(firstif, bstat);
              }
              else {
                // replace the if statement with its content
                first.getParent().replaceStatement(first, firstif.getIfstat());

                // lift closures
                for (StatEdge prededge : elseedge.getDestination().getPredecessorEdges(EdgeType.BREAK)) {
                  if (stat.containsStatementStrict(prededge.closure)) {
                    stat.addLabeledEdge(prededge);
                  }
                }

                LabelHelper.lowClosures(stat);
              }

              return true;
            }
          }
        }
      }
    }
    return false;
  }

  public static boolean isDirectPath(Statement stat, Statement endstat) {

    Set<Statement> setStat = stat.getNeighboursSet(EdgeType.DIRECT_ALL, EdgeDirection.FORWARD);
    if (setStat.isEmpty()) {
      Statement parent = stat.getParent();
      if (parent == null) {
        return false;
      }
      else {
        switch (parent.type) {
          case ROOT:
            return endstat.type == StatementType.DUMMY_EXIT;
          case DO:
            return (endstat == parent);
          case SWITCH:
            SwitchStatement swst = (SwitchStatement)parent;
            for (int i = 0; i < swst.getCaseStatements().size() - 1; i++) {
              Statement stt = swst.getCaseStatements().get(i);
              if (stt == stat) {
                Statement stnext = swst.getCaseStatements().get(i + 1);

                if (stnext.getExprents() != null && stnext.getExprents().isEmpty()) {
                  stnext = stnext.getAllSuccessorEdges().get(0).getDestination();
                }
                return (endstat == stnext);
              }
            }
          default:
            return isDirectPath(parent, endstat);
        }
      }
    }
    else {
      return setStat.contains(endstat);
    }
  }

  private static void matchFor(DoStatement stat) {
    Exprent lastDoExprent, initDoExprent;
    Statement lastData, preData = null;

    // get last exprent
    lastData = getLastDirectData(stat.getFirst());
    if (lastData == null || lastData.getExprents().isEmpty()) {
      return;
    }

    List<Exprent> lstExpr = lastData.getExprents();
    lastDoExprent = lstExpr.get(lstExpr.size() - 1);

    boolean issingle = false;
    if (lstExpr.size() == 1) {  // single exprent
      if (lastData.getAllPredecessorEdges().size() > 1) { // break edges
        issingle = true;
      }
    }

    boolean haslast = issingle || lastDoExprent.type == Exprent.EXPRENT_ASSIGNMENT || lastDoExprent.type == Exprent.EXPRENT_FUNCTION;
    if (!haslast) {
      return;
    }

    boolean hasinit = false;

    // search for an initializing exprent
    Statement current = stat;
    while (true) {
      Statement parent = current.getParent();
      if (parent == null) {
        break;
      }

      if (parent.type == StatementType.SEQUENCE) {
        if (current == parent.getFirst()) {
          current = parent;
        }
        else {
          preData = current.getNeighbours(EdgeType.REGULAR, EdgeDirection.BACKWARD).get(0);
          preData = getLastDirectData(preData);
          if (preData != null && !preData.getExprents().isEmpty()) {
            initDoExprent = preData.getExprents().get(preData.getExprents().size() - 1);
            if (initDoExprent.type == Exprent.EXPRENT_ASSIGNMENT) {
              hasinit = true;
            }
          }
          break;
        }
      }
      else {
        break;
      }
    }

    if (hasinit || issingle) {  // FIXME: issingle sufficient?
      Set<Statement> set = stat.getNeighboursSet(EdgeType.CONTINUE, EdgeDirection.BACKWARD);
      set.remove(lastData);

      if (!set.isEmpty()) {
        return;
      }

      stat.setLoopType(LoopType.FOR);
      if (hasinit) {
        stat.setInitExprent(preData.getExprents().remove(preData.getExprents().size() - 1));
      }
      stat.setIncExprent(lastData.getExprents().remove(lastData.getExprents().size() - 1));
    }

    if (lastData.getExprents().isEmpty()) {
      List<StatEdge> lst = lastData.getAllSuccessorEdges();
      if (!lst.isEmpty()) {
        lastData.removeSuccessor(lst.get(0));
      }
      removeLastEmptyStatement(stat, lastData);
    }
  }

  private static void removeLastEmptyStatement(DoStatement dostat, Statement stat) {

    if (stat == dostat.getFirst()) {
      BasicBlockStatement bstat = new BasicBlockStatement(new BasicBlock(
        DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
      bstat.setExprents(new ArrayList<>());
      dostat.replaceStatement(stat, bstat);
    }
    else {
      for (StatEdge edge : stat.getAllPredecessorEdges()) {
        edge.getSource().changeEdgeType(EdgeDirection.FORWARD, edge, EdgeType.CONTINUE);

        stat.removePredecessor(edge);
        edge.getSource().changeEdgeNode(EdgeDirection.FORWARD, edge, dostat);
        dostat.addPredecessor(edge);

        dostat.addLabeledEdge(edge);
      }

      // parent is a sequence statement
      stat.getParent().getStats().removeWithKey(stat.id);
    }
  }

  private static Statement getLastDirectData(Statement stat) {
    if (stat.getExprents() != null) {
      return stat;
    }

    if (stat.type == StatementType.SEQUENCE) {
      for (int i = stat.getStats().size() - 1; i >= 0; i--) {
        Statement tmp = getLastDirectData(stat.getStats().get(i));
        if (tmp == null || !tmp.getExprents().isEmpty()) {
          return tmp;
        }
      }
    }
    return null;
  }
}