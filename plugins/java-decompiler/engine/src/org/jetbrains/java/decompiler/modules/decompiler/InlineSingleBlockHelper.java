// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeDirection;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;

import java.util.ArrayList;
import java.util.List;


public final class InlineSingleBlockHelper {


  public static boolean inlineSingleBlocks(RootStatement root) {

    boolean res = inlineSingleBlocksRec(root);

    if (res) {
      SequenceHelper.condenseSequences(root);
    }

    return res;
  }

  private static boolean inlineSingleBlocksRec(Statement stat) {

    boolean res = false;

    for (Statement st : stat.getStats()) {
      res |= inlineSingleBlocksRec(st);
    }

    if (stat.type == StatementType.SEQUENCE) {

      SequenceStatement seq = (SequenceStatement)stat;
      for (int i = 1; i < seq.getStats().size(); i++) {
        if (isInlineable(seq, i)) {
          inlineBlock(seq, i);
          return true;
        }
      }
    }

    return res;
  }

  private static void inlineBlock(SequenceStatement seq, int index) {

    Statement first = seq.getStats().get(index);
    Statement pre = seq.getStats().get(index - 1);
    pre.removeSuccessor(pre.getAllSuccessorEdges().get(0));   // single regular edge

    StatEdge edge = first.getPredecessorEdges(EdgeType.BREAK).get(0);
    Statement source = edge.getSource();
    Statement parent = source.getParent();
    source.removeSuccessor(edge);

    List<Statement> lst = new ArrayList<>();
    for (int i = seq.getStats().size() - 1; i >= index; i--) {
      lst.add(0, seq.getStats().remove(i));
    }

    if (parent.type == StatementType.IF && ((IfStatement)parent).iftype == IfStatement.IFTYPE_IF &&
        source == parent.getFirst()) {
      IfStatement ifparent = (IfStatement)parent;
      SequenceStatement block = new SequenceStatement(lst);
      block.setAllParent();

      StatEdge newedge = new StatEdge(EdgeType.REGULAR, source, block);
      source.addSuccessor(newedge);
      ifparent.setIfEdge(newedge);
      ifparent.setIfstat(block);

      ifparent.getStats().addWithKey(block, block.id);
      block.setParent(ifparent);
    }
    else {
      lst.add(0, source);

      SequenceStatement block = new SequenceStatement(lst);
      block.setAllParent();

      parent.replaceStatement(source, block);

      // LabelHelper.lowContinueLabels not applicable because of forward continue edges
      // LabelHelper.lowContinueLabels(block, new HashSet<StatEdge>());
      // do it by hand
      for (StatEdge prededge : block.getPredecessorEdges(EdgeType.CONTINUE)) {

        block.removePredecessor(prededge);
        prededge.getSource().changeEdgeNode(EdgeDirection.FORWARD, prededge, source);
        source.addPredecessor(prededge);

        source.addLabeledEdge(prededge);
      }


      if (parent.type == StatementType.SWITCH) {
        ((SwitchStatement)parent).sortEdgesAndNodes();
      }

      source.addSuccessor(new StatEdge(EdgeType.REGULAR, source, first));
    }
  }

  private static boolean isInlineable(SequenceStatement seq, int index) {

    Statement first = seq.getStats().get(index);
    Statement pre = seq.getStats().get(index - 1);

    if (pre.hasBasicSuccEdge()) {
      return false;
    }


    List<StatEdge> lst = first.getPredecessorEdges(EdgeType.BREAK);

    if (lst.size() == 1) {
      StatEdge edge = lst.get(0);

      if (sameCatchRanges(edge)) {
        if (!edge.explicit) {
          for (int i = index; i < seq.getStats().size(); i++) {
            if (!noExitLabels(seq.getStats().get(i), seq)) {
              return false;
            }
          }
        }
        return true;
      }
      // FIXME: count labels properly
    }

    return false;
  }

  private static boolean sameCatchRanges(StatEdge edge) {

    Statement from = edge.getSource();
    Statement to = edge.getDestination();

    while (true) {

      Statement parent = from.getParent();
      if (parent.containsStatementStrict(to)) {
        break;
      }

      if (parent.type == StatementType.TRY_CATCH ||
          parent.type == StatementType.CATCH_ALL) {
        if (parent.getFirst() == from) {
          return false;
        }
      }
      else if (parent.type == StatementType.SYNCHRONIZED) {
        if (parent.getStats().get(1) == from) {
          return false;
        }
      }

      from = parent;
    }

    return true;
  }

  private static boolean noExitLabels(Statement block, Statement sequence) {

    for (StatEdge edge : block.getAllSuccessorEdges()) {
      if (edge.getType() != EdgeType.REGULAR && edge.getDestination().type != StatementType.DUMMY_EXIT) {
        if (!sequence.containsStatementStrict(edge.getDestination())) {
          return false;
        }
      }
    }

    for (Statement st : block.getStats()) {
      if (!noExitLabels(st, sequence)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isBreakEdgeLabeled(Statement source, Statement closure) {
    if (closure.type == StatementType.DO || closure.type == StatementType.SWITCH) {
      Statement parent = source.getParent();
      return parent != closure &&
             (parent.type == StatementType.DO || parent.type == StatementType.SWITCH || isBreakEdgeLabeled(parent, closure));
    }
    else {
      return true;
    }
  }
}