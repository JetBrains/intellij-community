// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeDirection;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement.LoopType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class ExitHelper {
  public static boolean condenseExits(RootStatement root) {
    int changed = integrateExits(root);

    if (changed > 0) {
      cleanUpUnreachableBlocks(root);
      SequenceHelper.condenseSequences(root);
    }

    return (changed > 0);
  }

  private static void cleanUpUnreachableBlocks(Statement stat) {
    boolean found;
    do {
      found = false;

      for (int i = 0; i < stat.getStats().size(); i++) {
        Statement st = stat.getStats().get(i);

        cleanUpUnreachableBlocks(st);

        if (st.type == StatementType.SEQUENCE && st.getStats().size() > 1) {

          Statement last = st.getStats().getLast();
          Statement secondlast = st.getStats().get(st.getStats().size() - 2);

          if (last.getExprents() == null || !last.getExprents().isEmpty()) {
            if (!secondlast.hasBasicSuccEdge()) {

              Set<Statement> set = last.getNeighboursSet(EdgeType.DIRECT_ALL, EdgeDirection.BACKWARD);
              set.remove(secondlast);

              if (set.isEmpty()) {
                last.setExprents(new ArrayList<>());
                found = true;
                break;
              }
            }
          }
        }
      }
    }
    while (found);
  }

  private static int integrateExits(Statement stat) {
    int ret = 0;
    Statement dest;

    if (stat.getExprents() == null) {
      while (true) {
        int changed = 0;

        for (Statement st : stat.getStats()) {
          changed = integrateExits(st);
          if (changed > 0) {
            ret = 1;
            break;
          }
        }

        if (changed == 0) {
          break;
        }
      }

      if (stat.type == StatementType.IF) {
        IfStatement ifst = (IfStatement)stat;
        if (ifst.getIfstat() == null) {
          StatEdge ifedge = ifst.getIfEdge();
          dest = isExitEdge(ifedge);
          if (dest != null) {
            BasicBlockStatement bstat = new BasicBlockStatement(new BasicBlock(
              DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
            bstat.setExprents(DecHelper.copyExprentList(dest.getExprents()));

            ifst.getFirst().removeSuccessor(ifedge);
            StatEdge newedge = new StatEdge(EdgeType.REGULAR, ifst.getFirst(), bstat);
            ifst.getFirst().addSuccessor(newedge);
            ifst.setIfEdge(newedge);
            ifst.setIfstat(bstat);
            ifst.getStats().addWithKey(bstat, bstat.id);
            bstat.setParent(ifst);

            StatEdge oldexitedge = dest.getAllSuccessorEdges().get(0);
            StatEdge newexitedge = new StatEdge(EdgeType.BREAK, bstat, oldexitedge.getDestination());
            bstat.addSuccessor(newexitedge);
            oldexitedge.closure.addLabeledEdge(newexitedge);
            ret = 1;
          }
        }
      }
    }


    if (stat.getAllSuccessorEdges().size() == 1 &&
        stat.getAllSuccessorEdges().get(0).getType() == EdgeType.BREAK &&
        stat.getLabelEdges().isEmpty()) {
      Statement parent = stat.getParent();
      if (stat != parent.getFirst() || (parent.type != StatementType.IF &&
                                        parent.type != StatementType.SWITCH)) {

        StatEdge destedge = stat.getAllSuccessorEdges().get(0);
        dest = isExitEdge(destedge);
        if (dest != null) {
          stat.removeSuccessor(destedge);

          BasicBlockStatement bstat = new BasicBlockStatement(new BasicBlock(
            DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
          bstat.setExprents(DecHelper.copyExprentList(dest.getExprents()));

          StatEdge oldexitedge = dest.getAllSuccessorEdges().get(0);
          StatEdge newexitedge = new StatEdge(EdgeType.BREAK, bstat, oldexitedge.getDestination());
          bstat.addSuccessor(newexitedge);
          oldexitedge.closure.addLabeledEdge(newexitedge);

          SequenceStatement block = new SequenceStatement(Arrays.asList(stat, bstat));
          block.setAllParent();

          parent.replaceStatement(stat, block);
          // LabelHelper.lowContinueLabels not applicable because of forward continue edges
          // LabelHelper.lowContinueLabels(block, new HashSet<StatEdge>());
          // do it by hand
          for (StatEdge prededge : block.getPredecessorEdges(EdgeType.CONTINUE)) {
            block.removePredecessor(prededge);
            prededge.getSource().changeEdgeNode(EdgeDirection.FORWARD, prededge, stat);
            stat.addPredecessor(prededge);
            stat.addLabeledEdge(prededge);
          }

          stat.addSuccessor(new StatEdge(EdgeType.REGULAR, stat, bstat));

          for (StatEdge edge : dest.getAllPredecessorEdges()) {
            if (!edge.explicit && stat.containsStatementStrict(edge.getSource()) &&
                MergeHelper.isDirectPath(edge.getSource().getParent(), bstat)) {

              dest.removePredecessor(edge);
              edge.getSource().changeEdgeNode(EdgeDirection.FORWARD, edge, bstat);
              bstat.addPredecessor(edge);

              if (!stat.containsStatementStrict(edge.closure)) {
                stat.addLabeledEdge(edge);
              }
            }
          }

          ret = 2;
        }
      }
    }

    return ret;
  }

  private static Statement isExitEdge(StatEdge edge) {
    Statement dest = edge.getDestination();

    if (edge.getType() == EdgeType.BREAK && dest.type == StatementType.BASIC_BLOCK && edge.explicit && (edge.labeled || isOnlyEdge(edge))) {
      List<Exprent> data = dest.getExprents();

      if (data != null && data.size() == 1) {
        if (data.get(0).type == Exprent.EXPRENT_EXIT) {
          return dest;
        }
      }
    }

    return null;
  }

  private static boolean isOnlyEdge(StatEdge edge) {
    Statement stat = edge.getDestination();

    for (StatEdge ed : stat.getAllPredecessorEdges()) {
      if (ed != edge) {
        if (ed.getType() == EdgeType.REGULAR) {
          Statement source = ed.getSource();

          if (source.type == StatementType.BASIC_BLOCK || (source.type == StatementType.IF &&
                                                           ((IfStatement)source).iftype == IfStatement.IFTYPE_IF) ||
              (source.type == StatementType.DO && ((DoStatement)source).getLoopType() != LoopType.DO)) {
            return false;
          }
        }
        else {
          return false;
        }
      }
    }

    return true;
  }

  public static void removeRedundantReturns(RootStatement root) {
    DummyExitStatement dummyExit = root.getDummyExit();

    for (StatEdge edge : dummyExit.getAllPredecessorEdges()) {
      if (!edge.explicit) {
        Statement source = edge.getSource();
        List<Exprent> lstExpr = source.getExprents();
        if (lstExpr != null && !lstExpr.isEmpty()) {
          Exprent expr = lstExpr.get(lstExpr.size() - 1);
          if (expr.type == Exprent.EXPRENT_EXIT) {
            ExitExprent ex = (ExitExprent)expr;
            if (ex.getExitType() == ExitExprent.EXIT_RETURN && ex.getValue() == null) {
              // remove redundant return
              dummyExit.addBytecodeOffsets(ex.bytecode);
              lstExpr.remove(lstExpr.size() - 1);
            }
          }
        }
      }
    }
  }
}