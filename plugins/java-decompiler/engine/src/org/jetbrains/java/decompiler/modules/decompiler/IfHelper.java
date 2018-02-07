// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.IfExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;

import java.util.*;

public class IfHelper {
  public static boolean mergeAllIfs(RootStatement root) {
    boolean res = mergeAllIfsRec(root, new HashSet<>());
    if (res) {
      SequenceHelper.condenseSequences(root);
    }
    return res;
  }

  private static boolean mergeAllIfsRec(Statement stat, Set<Integer> setReorderedIfs) {
    boolean res = false;

    if (stat.getExprents() == null) {
      while (true) {
        boolean changed = false;

        for (Statement st : stat.getStats()) {
          res |= mergeAllIfsRec(st, setReorderedIfs);

          // collapse composed if's
          if (changed = mergeIfs(st, setReorderedIfs)) {
            break;
          }
        }

        res |= changed;

        if (!changed) {
          break;
        }
      }
    }

    return res;
  }

  public static boolean mergeIfs(Statement statement, Set<Integer> setReorderedIfs) {
    if (statement.type != Statement.TYPE_IF && statement.type != Statement.TYPE_SEQUENCE) {
      return false;
    }

    boolean res = false;

    while (true) {
      boolean updated = false;

      List<Statement> lst = new ArrayList<>();
      if (statement.type == Statement.TYPE_IF) {
        lst.add(statement);
      }
      else {
        lst.addAll(statement.getStats());
      }

      boolean stsingle = (lst.size() == 1);

      for (Statement stat : lst) {
        if (stat.type == Statement.TYPE_IF) {
          IfNode rtnode = buildGraph((IfStatement)stat, stsingle);

          if (rtnode == null) {
            continue;
          }

          if (updated = collapseIfIf(rtnode)) {
            break;
          }

          if (!setReorderedIfs.contains(stat.id)) {
            if (updated = collapseIfElse(rtnode)) {
              break;
            }

            if (updated = collapseElse(rtnode)) {
              break;
            }
          }

          if (updated = reorderIf((IfStatement)stat)) {
            setReorderedIfs.add(stat.id);
            break;
          }
        }
      }

      if (!updated) {
        break;
      }

      res |= true;
    }

    return res;
  }

  private static boolean collapseIfIf(IfNode rtnode) {
    if (rtnode.edgetypes.get(0) == 0) {
      IfNode ifbranch = rtnode.succs.get(0);
      if (ifbranch.succs.size() == 2) {

        // if-if branch
        if (ifbranch.succs.get(1).value == rtnode.succs.get(1).value) {

          IfStatement ifparent = (IfStatement)rtnode.value;
          IfStatement ifchild = (IfStatement)ifbranch.value;
          Statement ifinner = ifbranch.succs.get(0).value;

          if (ifchild.getFirst().getExprents().isEmpty()) {

            ifparent.getFirst().removeSuccessor(ifparent.getIfEdge());
            ifchild.removeSuccessor(ifchild.getAllSuccessorEdges().get(0));
            ifparent.getStats().removeWithKey(ifchild.id);

            if (ifbranch.edgetypes.get(0) == 1) { // target null

              ifparent.setIfstat(null);

              StatEdge ifedge = ifchild.getIfEdge();

              ifchild.getFirst().removeSuccessor(ifedge);
              ifedge.setSource(ifparent.getFirst());

              if (ifedge.closure == ifchild) {
                ifedge.closure = null;
              }
              ifparent.getFirst().addSuccessor(ifedge);

              ifparent.setIfEdge(ifedge);
            }
            else {
              ifchild.getFirst().removeSuccessor(ifchild.getIfEdge());

              StatEdge ifedge = new StatEdge(StatEdge.TYPE_REGULAR, ifparent.getFirst(), ifinner);
              ifparent.getFirst().addSuccessor(ifedge);
              ifparent.setIfEdge(ifedge);
              ifparent.setIfstat(ifinner);

              ifparent.getStats().addWithKey(ifinner, ifinner.id);
              ifinner.setParent(ifparent);

              if (!ifinner.getAllSuccessorEdges().isEmpty()) {
                StatEdge edge = ifinner.getAllSuccessorEdges().get(0);
                if (edge.closure == ifchild) {
                  edge.closure = null;
                }
              }
            }

            // merge if conditions
            IfExprent statexpr = ifparent.getHeadexprent();

            List<Exprent> lstOperands = new ArrayList<>();
            lstOperands.add(statexpr.getCondition());
            lstOperands.add(ifchild.getHeadexprent().getCondition());

            statexpr.setCondition(new FunctionExprent(FunctionExprent.FUNCTION_CADD, lstOperands, null));
            statexpr.addBytecodeOffsets(ifchild.getHeadexprent().bytecode);

            return true;
          }
        }
      }
    }

    return false;
  }

  private static boolean collapseIfElse(IfNode rtnode) {
    if (rtnode.edgetypes.get(0) == 0) {
      IfNode ifbranch = rtnode.succs.get(0);
      if (ifbranch.succs.size() == 2) {
        // if-else branch
        if (ifbranch.succs.get(0).value == rtnode.succs.get(1).value) {

          IfStatement ifparent = (IfStatement)rtnode.value;
          IfStatement ifchild = (IfStatement)ifbranch.value;

          if (ifchild.getFirst().getExprents().isEmpty()) {

            ifparent.getFirst().removeSuccessor(ifparent.getIfEdge());
            ifchild.getFirst().removeSuccessor(ifchild.getIfEdge());
            ifparent.getStats().removeWithKey(ifchild.id);

            if (ifbranch.edgetypes.get(1) == 1 &&
                ifbranch.edgetypes.get(0) == 1) { // target null

              ifparent.setIfstat(null);

              StatEdge ifedge = ifchild.getAllSuccessorEdges().get(0);

              ifchild.removeSuccessor(ifedge);
              ifedge.setSource(ifparent.getFirst());
              ifparent.getFirst().addSuccessor(ifedge);

              ifparent.setIfEdge(ifedge);
            }
            else {
              throw new RuntimeException("inconsistent if structure!");
            }

            // merge if conditions
            IfExprent statexpr = ifparent.getHeadexprent();

            List<Exprent> lstOperands = new ArrayList<>();
            lstOperands.add(statexpr.getCondition());
            lstOperands.add(new FunctionExprent(FunctionExprent.FUNCTION_BOOL_NOT, ifchild.getHeadexprent().getCondition(), null));
            statexpr.setCondition(new FunctionExprent(FunctionExprent.FUNCTION_CADD, lstOperands, null));
            statexpr.addBytecodeOffsets(ifchild.getHeadexprent().bytecode);

            return true;
          }
        }
      }
    }

    return false;
  }

  private static boolean collapseElse(IfNode rtnode) {
    if (rtnode.edgetypes.get(1) == 0) {
      IfNode elsebranch = rtnode.succs.get(1);
      if (elsebranch.succs.size() == 2) {

        // else-if or else-else branch
        int path = elsebranch.succs.get(1).value == rtnode.succs.get(0).value ? 2 :
                   (elsebranch.succs.get(0).value == rtnode.succs.get(0).value ? 1 : 0);

        if (path > 0) {

          IfStatement firstif = (IfStatement)rtnode.value;
          IfStatement secondif = (IfStatement)elsebranch.value;
          Statement parent = firstif.getParent();

          if (secondif.getFirst().getExprents().isEmpty()) {

            firstif.getFirst().removeSuccessor(firstif.getIfEdge());

            // remove first if
            firstif.removeAllSuccessors(secondif);

            for (StatEdge edge : firstif.getAllPredecessorEdges()) {
              if (!firstif.containsStatementStrict(edge.getSource())) {
                firstif.removePredecessor(edge);
                edge.getSource().changeEdgeNode(Statement.DIRECTION_FORWARD, edge, secondif);
                secondif.addPredecessor(edge);
              }
            }

            parent.getStats().removeWithKey(firstif.id);
            if (parent.getFirst() == firstif) {
              parent.setFirst(secondif);
            }

            // merge if conditions
            IfExprent statexpr = secondif.getHeadexprent();

            List<Exprent> lstOperands = new ArrayList<>();
            lstOperands.add(firstif.getHeadexprent().getCondition());

            if (path == 2) {
              lstOperands.set(0, new FunctionExprent(FunctionExprent.FUNCTION_BOOL_NOT, lstOperands.get(0), null));
            }

            lstOperands.add(statexpr.getCondition());

            statexpr
              .setCondition(new FunctionExprent(path == 1 ? FunctionExprent.FUNCTION_COR : FunctionExprent.FUNCTION_CADD, lstOperands, null));

            if (secondif.getFirst().getExprents().isEmpty() &&
                !firstif.getFirst().getExprents().isEmpty()) {

              secondif.replaceStatement(secondif.getFirst(), firstif.getFirst());
            }

            return true;
          }
        }
      }
      else if (elsebranch.succs.size() == 1) {
        if (elsebranch.succs.get(0).value == rtnode.succs.get(0).value) {
          IfStatement firstif = (IfStatement)rtnode.value;
          Statement second = elsebranch.value;

          firstif.removeAllSuccessors(second);

          for (StatEdge edge : second.getAllSuccessorEdges()) {
            second.removeSuccessor(edge);
            edge.setSource(firstif);
            firstif.addSuccessor(edge);
          }

          StatEdge ifedge = firstif.getIfEdge();
          firstif.getFirst().removeSuccessor(ifedge);

          second.addSuccessor(new StatEdge(ifedge.getType(), second, ifedge.getDestination(), ifedge.closure));

          StatEdge newifedge = new StatEdge(StatEdge.TYPE_REGULAR, firstif.getFirst(), second);
          firstif.getFirst().addSuccessor(newifedge);
          firstif.setIfstat(second);

          firstif.getStats().addWithKey(second, second.id);
          second.setParent(firstif);

          firstif.getParent().getStats().removeWithKey(second.id);

          // negate the if condition
          IfExprent statexpr = firstif.getHeadexprent();
          statexpr
            .setCondition(new FunctionExprent(FunctionExprent.FUNCTION_BOOL_NOT, statexpr.getCondition(), null));

          return true;
        }
      }
    }

    return false;
  }

  private static IfNode buildGraph(IfStatement stat, boolean stsingle) {
    if (stat.iftype == IfStatement.IFTYPE_IFELSE) {
      return null;
    }

    IfNode res = new IfNode(stat);

    // if branch
    Statement ifchild = stat.getIfstat();
    if (ifchild == null) {
      StatEdge edge = stat.getIfEdge();
      res.addChild(new IfNode(edge.getDestination()), 1);
    }
    else {
      IfNode ifnode = new IfNode(ifchild);
      res.addChild(ifnode, 0);
      if (ifchild.type == Statement.TYPE_IF && ((IfStatement)ifchild).iftype == IfStatement.IFTYPE_IF) {
        IfStatement stat2 = (IfStatement)ifchild;
        Statement ifchild2 = stat2.getIfstat();
        if (ifchild2 == null) {
          StatEdge edge = stat2.getIfEdge();
          ifnode.addChild(new IfNode(edge.getDestination()), 1);
        }
        else {
          ifnode.addChild(new IfNode(ifchild2), 0);
        }
      }

      if (!ifchild.getAllSuccessorEdges().isEmpty()) {
        ifnode.addChild(new IfNode(ifchild.getAllSuccessorEdges().get(0).getDestination()), 1);
      }
    }

    // else branch
    StatEdge edge = stat.getAllSuccessorEdges().get(0);
    Statement elsechild = edge.getDestination();
    IfNode elsenode = new IfNode(elsechild);

    if (stsingle || edge.getType() != StatEdge.TYPE_REGULAR) {
      res.addChild(elsenode, 1);
    }
    else {
      res.addChild(elsenode, 0);
      if (elsechild.type == Statement.TYPE_IF && ((IfStatement)elsechild).iftype == IfStatement.IFTYPE_IF) {
        IfStatement stat2 = (IfStatement)elsechild;
        Statement ifchild2 = stat2.getIfstat();
        if (ifchild2 == null) {
          elsenode.addChild(new IfNode(stat2.getIfEdge().getDestination()), 1);
        }
        else {
          elsenode.addChild(new IfNode(ifchild2), 0);
        }
      }

      if (!elsechild.getAllSuccessorEdges().isEmpty()) {
        elsenode.addChild(new IfNode(elsechild.getAllSuccessorEdges().get(0).getDestination()), 1);
      }
    }

    return res;
  }

  // FIXME: rewrite the entire method!!! keep in mind finally exits!!
  private static boolean reorderIf(IfStatement ifstat) {
    if (ifstat.iftype == IfStatement.IFTYPE_IFELSE) {
      return false;
    }

    boolean ifdirect, elsedirect;
    boolean noifstat = false, noelsestat;
    boolean ifdirectpath = false, elsedirectpath = false;

    Statement parent = ifstat.getParent();
    Statement from = parent.type == Statement.TYPE_SEQUENCE ? parent : ifstat;

    Statement next = getNextStatement(from);

    if (ifstat.getIfstat() == null) {
      noifstat = true;

      ifdirect = ifstat.getIfEdge().getType() == StatEdge.TYPE_FINALLYEXIT ||
                 MergeHelper.isDirectPath(from, ifstat.getIfEdge().getDestination());
    }
    else {
      List<StatEdge> lstSuccs = ifstat.getIfstat().getAllSuccessorEdges();
      ifdirect = !lstSuccs.isEmpty() && lstSuccs.get(0).getType() == StatEdge.TYPE_FINALLYEXIT ||
                 hasDirectEndEdge(ifstat.getIfstat(), from);
    }

    Statement last = parent.type == Statement.TYPE_SEQUENCE ? parent.getStats().getLast() : ifstat;
    noelsestat = (last == ifstat);

    elsedirect = !last.getAllSuccessorEdges().isEmpty() && last.getAllSuccessorEdges().get(0).getType() == StatEdge.TYPE_FINALLYEXIT ||
                 hasDirectEndEdge(last, from);

    if (!noelsestat && existsPath(ifstat, ifstat.getAllSuccessorEdges().get(0).getDestination())) {
      return false;
    }

    if (!ifdirect && !noifstat) {
      ifdirectpath = existsPath(ifstat, next);
    }

    if (!elsedirect && !noelsestat) {
      SequenceStatement sequence = (SequenceStatement)parent;

      for (int i = sequence.getStats().size() - 1; i >= 0; i--) {
        Statement sttemp = sequence.getStats().get(i);
        if (sttemp == ifstat) {
          break;
        }
        else {
          if (elsedirectpath = existsPath(sttemp, next)) {
            break;
          }
        }
      }
    }

    if ((ifdirect || ifdirectpath) && (elsedirect || elsedirectpath) && !noifstat && !noelsestat) {  // if - then - else

      SequenceStatement sequence = (SequenceStatement)parent;

      // build and cut the new else statement
      List<Statement> lst = new ArrayList<>();
      for (int i = sequence.getStats().size() - 1; i >= 0; i--) {
        Statement sttemp = sequence.getStats().get(i);
        if (sttemp == ifstat) {
          break;
        }
        else {
          lst.add(0, sttemp);
        }
      }

      Statement stelse;
      if (lst.size() == 1) {
        stelse = lst.get(0);
      }
      else {
        stelse = new SequenceStatement(lst);
        stelse.setAllParent();
      }

      ifstat.removeSuccessor(ifstat.getAllSuccessorEdges().get(0));
      for (Statement st : lst) {
        sequence.getStats().removeWithKey(st.id);
      }

      StatEdge elseedge = new StatEdge(StatEdge.TYPE_REGULAR, ifstat.getFirst(), stelse);
      ifstat.getFirst().addSuccessor(elseedge);
      ifstat.setElsestat(stelse);
      ifstat.setElseEdge(elseedge);

      ifstat.getStats().addWithKey(stelse, stelse.id);
      stelse.setParent(ifstat);

      //			if(next.type != Statement.TYPE_DUMMYEXIT && (ifdirect || elsedirect)) {
      //	 			StatEdge breakedge = new StatEdge(StatEdge.TYPE_BREAK, ifstat, next);
      //				sequence.addLabeledEdge(breakedge);
      //				ifstat.addSuccessor(breakedge);
      //			}

      ifstat.iftype = IfStatement.IFTYPE_IFELSE;
    }
    else if (ifdirect && (!elsedirect || (noifstat && !noelsestat))) {  // if - then

      // negate the if condition
      IfExprent statexpr = ifstat.getHeadexprent();
      statexpr.setCondition(new FunctionExprent(FunctionExprent.FUNCTION_BOOL_NOT, statexpr.getCondition(), null));

      if (noelsestat) {
        StatEdge ifedge = ifstat.getIfEdge();
        StatEdge elseedge = ifstat.getAllSuccessorEdges().get(0);

        if (noifstat) {
          ifstat.getFirst().removeSuccessor(ifedge);
          ifstat.removeSuccessor(elseedge);

          ifedge.setSource(ifstat);
          elseedge.setSource(ifstat.getFirst());

          ifstat.addSuccessor(ifedge);
          ifstat.getFirst().addSuccessor(elseedge);

          ifstat.setIfEdge(elseedge);
        }
        else {
          Statement ifbranch = ifstat.getIfstat();
          SequenceStatement newseq = new SequenceStatement(Arrays.asList(ifstat, ifbranch));

          ifstat.getFirst().removeSuccessor(ifedge);
          ifstat.getStats().removeWithKey(ifbranch.id);
          ifstat.setIfstat(null);

          ifstat.removeSuccessor(elseedge);
          elseedge.setSource(ifstat.getFirst());
          ifstat.getFirst().addSuccessor(elseedge);

          ifstat.setIfEdge(elseedge);

          ifstat.getParent().replaceStatement(ifstat, newseq);
          newseq.setAllParent();

          ifstat.addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR, ifstat, ifbranch));
        }
      }
      else {

        SequenceStatement sequence = (SequenceStatement)parent;

        // build and cut the new else statement
        List<Statement> lst = new ArrayList<>();
        for (int i = sequence.getStats().size() - 1; i >= 0; i--) {
          Statement sttemp = sequence.getStats().get(i);
          if (sttemp == ifstat) {
            break;
          }
          else {
            lst.add(0, sttemp);
          }
        }

        Statement stelse;
        if (lst.size() == 1) {
          stelse = lst.get(0);
        }
        else {
          stelse = new SequenceStatement(lst);
          stelse.setAllParent();
        }

        ifstat.removeSuccessor(ifstat.getAllSuccessorEdges().get(0));
        for (Statement st : lst) {
          sequence.getStats().removeWithKey(st.id);
        }

        if (noifstat) {
          StatEdge ifedge = ifstat.getIfEdge();

          ifstat.getFirst().removeSuccessor(ifedge);
          ifedge.setSource(ifstat);
          ifstat.addSuccessor(ifedge);
        }
        else {
          Statement ifbranch = ifstat.getIfstat();

          ifstat.getFirst().removeSuccessor(ifstat.getIfEdge());
          ifstat.getStats().removeWithKey(ifbranch.id);

          ifstat.addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR, ifstat, ifbranch));

          sequence.getStats().addWithKey(ifbranch, ifbranch.id);
          ifbranch.setParent(sequence);
        }

        StatEdge newifedge = new StatEdge(StatEdge.TYPE_REGULAR, ifstat.getFirst(), stelse);
        ifstat.getFirst().addSuccessor(newifedge);
        ifstat.setIfstat(stelse);
        ifstat.setIfEdge(newifedge);

        ifstat.getStats().addWithKey(stelse, stelse.id);
        stelse.setParent(ifstat);
      }
    }
    else {
      return false;
    }

    return true;
  }

  private static boolean hasDirectEndEdge(Statement stat, Statement from) {

    for (StatEdge edge : stat.getAllSuccessorEdges()) {
      if (MergeHelper.isDirectPath(from, edge.getDestination())) {
        return true;
      }
    }

    if (stat.getExprents() == null) {
      switch (stat.type) {
        case Statement.TYPE_SEQUENCE:
          return hasDirectEndEdge(stat.getStats().getLast(), from);
        case Statement.TYPE_CATCHALL:
        case Statement.TYPE_TRYCATCH:
          for (Statement st : stat.getStats()) {
            if (hasDirectEndEdge(st, from)) {
              return true;
            }
          }
          break;
        case Statement.TYPE_IF:
          IfStatement ifstat = (IfStatement)stat;
          if (ifstat.iftype == IfStatement.IFTYPE_IFELSE) {
            return hasDirectEndEdge(ifstat.getIfstat(), from) ||
                   hasDirectEndEdge(ifstat.getElsestat(), from);
          }
          break;
        case Statement.TYPE_SYNCRONIZED:
          return hasDirectEndEdge(stat.getStats().get(1), from);
        case Statement.TYPE_SWITCH:
          for (Statement st : stat.getStats()) {
            if (hasDirectEndEdge(st, from)) {
              return true;
            }
          }
      }
    }

    return false;
  }

  private static Statement getNextStatement(Statement stat) {
    Statement parent = stat.getParent();
    switch (parent.type) {
      case Statement.TYPE_ROOT:
        return ((RootStatement)parent).getDummyExit();
      case Statement.TYPE_DO:
        return parent;
      case Statement.TYPE_SEQUENCE:
        SequenceStatement sequence = (SequenceStatement)parent;
        if (sequence.getStats().getLast() != stat) {
          for (int i = sequence.getStats().size() - 1; i >= 0; i--) {
            if (sequence.getStats().get(i) == stat) {
              return sequence.getStats().get(i + 1);
            }
          }
        }
    }

    return getNextStatement(parent);
  }

  private static boolean existsPath(Statement from, Statement to) {
    for (StatEdge edge : to.getAllPredecessorEdges()) {
      if (from.containsStatementStrict(edge.getSource())) {
        return true;
      }
    }

    return false;
  }

  private static class IfNode {
    public final Statement value;
    public final List<IfNode> succs = new ArrayList<>();
    public final List<Integer> edgetypes = new ArrayList<>();

    public IfNode(Statement value) {
      this.value = value;
    }

    public void addChild(IfNode child, int type) {
      succs.add(child);
      edgetypes.add(type);
    }
  }
}