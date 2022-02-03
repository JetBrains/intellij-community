// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeDirection;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement.LoopType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;


public final class LoopExtractHelper {


  public static boolean extractLoops(Statement root) {

    boolean res = (extractLoopsRec(root) != 0);

    if (res) {
      SequenceHelper.condenseSequences(root);
    }

    return res;
  }


  private static int extractLoopsRec(Statement stat) {

    boolean res = false;

    while (true) {

      boolean updated = false;

      for (Statement st : stat.getStats()) {
        int extr = extractLoopsRec(st);
        res |= (extr != 0);

        if (extr == 2) {
          updated = true;
          break;
        }
      }

      if (!updated) {
        break;
      }
    }

    if (stat.type == StatementType.DO) {
      if (extractLoop((DoStatement)stat)) {
        return 2;
      }
    }

    return res ? 1 : 0;
  }

  private static boolean extractLoop(DoStatement stat) {
    if (stat.getLoopType() != LoopType.DO) {
      return false;
    }

    for (StatEdge edge : stat.getLabelEdges()) {
      if (edge.getType() != EdgeType.CONTINUE && edge.getDestination().type != StatementType.DUMMY_EXIT) {
        return false;
      }
    }

    return extractLastIf(stat) || extractFirstIf(stat);
  }

  private static boolean extractLastIf(DoStatement stat) {

    // search for an if condition at the end of the loop
    Statement last = stat.getFirst();
    while (last.type == StatementType.SEQUENCE) {
      last = last.getStats().getLast();
    }

    if (last.type == StatementType.IF) {
      IfStatement lastif = (IfStatement)last;
      if (lastif.iftype == IfStatement.IFTYPE_IF && lastif.getIfstat() != null) {
        Statement ifstat = lastif.getIfstat();
        StatEdge elseedge = lastif.getAllSuccessorEdges().get(0);

        if (elseedge.getType() == EdgeType.CONTINUE && elseedge.closure == stat) {

          Set<Statement> set = stat.getNeighboursSet(EdgeType.CONTINUE, EdgeDirection.BACKWARD);
          set.remove(last);

          if (set.isEmpty()) { // no direct continues in a do{}while loop
            if (isExternStatement(stat, ifstat, ifstat)) {
              extractIfBlock(stat, lastif);
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean extractFirstIf(DoStatement stat) {

    // search for an if condition at the entrance of the loop
    Statement first = stat.getFirst();
    while (first.type == StatementType.SEQUENCE) {
      first = first.getFirst();
    }

    // found an if statement
    if (first.type == StatementType.IF) {
      IfStatement firstif = (IfStatement)first;

      if (firstif.getFirst().getExprents().isEmpty()) {

        if (firstif.iftype == IfStatement.IFTYPE_IF && firstif.getIfstat() != null) {
          Statement ifstat = firstif.getIfstat();

          if (isExternStatement(stat, ifstat, ifstat)) {
            extractIfBlock(stat, firstif);
            return true;
          }
        }
      }
    }
    return false;
  }


  private static boolean isExternStatement(DoStatement loop, Statement block, Statement stat) {

    for (StatEdge edge : stat.getAllSuccessorEdges()) {
      if (loop.containsStatement(edge.getDestination()) &&
          !block.containsStatement(edge.getDestination())) {
        return false;
      }
    }

    for (Statement st : stat.getStats()) {
      if (!isExternStatement(loop, block, st)) {
        return false;
      }
    }

    return true;
  }


  private static void extractIfBlock(DoStatement loop, IfStatement ifstat) {

    Statement target = ifstat.getIfstat();
    StatEdge ifedge = ifstat.getIfEdge();

    ifstat.setIfstat(null);
    ifedge.getSource().changeEdgeType(EdgeDirection.FORWARD, ifedge, EdgeType.BREAK);
    ifedge.closure = loop;
    ifstat.getStats().removeWithKey(target.id);

    loop.addLabeledEdge(ifedge);

    SequenceStatement block = new SequenceStatement(Arrays.asList(loop, target));
    loop.getParent().replaceStatement(loop, block);
    block.setAllParent();

    loop.addSuccessor(new StatEdge(EdgeType.REGULAR, loop, target));

    for (StatEdge edge : new ArrayList<>(block.getLabelEdges())) {
      if (edge.getType() == EdgeType.CONTINUE || edge == ifedge) {
        loop.addLabeledEdge(edge);
      }
    }

    for (StatEdge edge : block.getPredecessorEdges(EdgeType.CONTINUE)) {
      if (loop.containsStatementStrict(edge.getSource())) {
        block.removePredecessor(edge);
        edge.getSource().changeEdgeNode(EdgeDirection.FORWARD, edge, loop);
        loop.addPredecessor(edge);
      }
    }
  }
}
