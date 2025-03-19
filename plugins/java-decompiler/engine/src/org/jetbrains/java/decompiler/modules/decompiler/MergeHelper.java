// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement.LoopType;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeDirection;

public final class MergeHelper {
  /**
   * Enhances the loops in the provided statement tree.
   * Simple example:<p>
   * before: <pre>
   * {@code
   * Iterator var6 = a.iterator();
   * while(true) {
   *  if (!var6.hasNext()) {
   *    break;
   *  }
   *  A<String> s = (A)var6.next();
   * }
   * }
   * </pre>
   * after:
   * <pre>
   * {@code
   * for(A<String> s : a) {}
   * </pre>
   */
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
      case DO -> {

        // identify a while loop
        if (matchWhile(stat)) {
          if (!matchForEach(stat)) {
            matchFor(stat);
          }
        }
        else {
          // identify a do{}while loop
          //matchDoWhile(stat);
        }
      }
      case WHILE -> {
        if (!matchForEach(stat)) {
          matchFor(stat);
        }
      }
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

          if (stat.getConditionExprent() != null) {
            ifexpr.getCondition().addBytecodeOffsets(stat.getConditionExprent().bytecode);
          }
          ifexpr.getCondition().addBytecodeOffsets(lastif.getHeadexprent().bytecode);

          stat.setConditionExprent(ifexpr.getCondition());
          lastif.getFirst().removeSuccessor(ifedge);
          lastif.removeSuccessor(elseedge);

          // remove empty if
          List<Exprent> exprents = lastif.getFirst().getExprents();
          if (exprents != null && exprents.isEmpty()) {
            removeLastEmptyStatement(stat, lastif);
          }
          else {
            lastif.setExprents(exprents);

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

      List<Exprent> exprents = firstif.getFirst().getExprents();
      if (exprents != null && exprents.isEmpty()) {

        if (firstif.iftype == IfStatement.IFTYPE_IF) {
          if (firstif.getIfstat() == null) {
            StatEdge ifedge = firstif.getIfEdge();

            if (isDirectPath(stat, ifedge.getDestination()) || addContinueOrBreak(stat, ifedge)) {
              // exit condition identified
              stat.setLoopType(LoopType.WHILE);

              // negate condition (while header)
              IfExprent ifexpr = (IfExprent)firstif.getHeadexprent().copy();
                ifexpr.negateIf();

              if (stat.getConditionExprent() != null) {
                ifexpr.getCondition().addBytecodeOffsets(stat.getConditionExprent().bytecode);
              }
              ifexpr.getCondition().addBytecodeOffsets(firstif.getHeadexprent().bytecode);

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
          //else { // fix infinite loops
            StatEdge elseedge = firstif.getAllSuccessorEdges().get(0);
            if (isDirectPath(stat, elseedge.getDestination())) {
              // exit condition identified
              stat.setLoopType(LoopType.WHILE);

              // no need to negate the while condition
              IfExprent ifexpr = (IfExprent)firstif.getHeadexprent().copy();
              if (stat.getConditionExprent() != null) {
                ifexpr.getCondition().addBytecodeOffsets(stat.getConditionExprent().bytecode);
              }
              ifexpr.getCondition().addBytecodeOffsets(firstif.getHeadexprent().bytecode);
              stat.setConditionExprent(ifexpr.getCondition());

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
          //}
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
    if (lastData == null || lastData.getExprents() == null || lastData.getExprents().isEmpty()) {
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
          // we're not a basic block, so we can't dive inside for exprents
          if (preData.type != Statement.StatementType.BASIC_BLOCK) break;
          preData = getLastDirectData(preData);
          if (preData != null && preData.getExprents() != null && !preData.getExprents().isEmpty()) {
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
        Exprent exp = preData.getExprents().remove(preData.getExprents().size() - 1);
        if (stat.getInitExprent() != null) {
          exp.addBytecodeOffsets(stat.getInitExprent().bytecode);
        }
        stat.setInitExprent(exp);
      }
      Exprent exp = lastData.getExprents().remove(lastData.getExprents().size() - 1);
      if (stat.getIncExprent() != null) {
        exp.addBytecodeOffsets(stat.getIncExprent().bytecode);
      }
      stat.setIncExprent(exp);
    }

    cleanEmptyStatements(stat, lastData);
  }

  private static void cleanEmptyStatements(DoStatement dostat, Statement stat) {
    if (stat != null && stat.getExprents() != null && stat.getExprents().isEmpty()) {
      List<StatEdge> lst = stat.getAllSuccessorEdges();
      if (!lst.isEmpty()) {
        stat.removeSuccessor(lst.get(0));
      }
      removeLastEmptyStatement(dostat, stat);
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

    for (int i = stat.getStats().size() - 1; i >= 0; i--) {
      Statement tmp = getLastDirectData(stat.getStats().get(i));
      if (tmp == null || tmp.getExprents() != null && !tmp.getExprents().isEmpty()) {
        return tmp;
      }
    }
    return null;
  }

  private static boolean matchForEach(DoStatement stat) {
    AssignmentExprent firstDoExprent = null;
    //[0]: var4 = 0 - index
    //[1]: var3 = var1.length - max value
    //[2]: var2 = var1 - what is iterated
    AssignmentExprent[] initExprents = new AssignmentExprent[3];
    Statement firstData, preData = null, lastData;
    Exprent lastExprent = null;

    // search for an initializing exprent
    Statement current = stat;
    while (true) {
      Statement parent = current.getParent();
      if (parent == null) {
        break;
      }

      if (parent.type == Statement.StatementType.SEQUENCE) {
        if (current == parent.getFirst()) {
          current = parent;
        }
        else {
          preData = current.getNeighbours(StatEdge.EdgeType.REGULAR, EdgeDirection.BACKWARD).get(0);
          preData = getLastDirectData(preData);
          if (preData != null && preData.getExprents() != null && !preData.getExprents().isEmpty()) {
            int size = preData.getExprents().size();
            for (int x = 0; x<size && x < initExprents.length; x++) {
              Exprent exprent = preData.getExprents().get(size - 1 - x);
              if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
                initExprents[x] = (AssignmentExprent)exprent;
              }
            }
          }
          break;
        }
      }
      else {
        break;
      }
    }

    firstData = getFirstDirectData(stat.getFirst());
    if (firstData != null && firstData.getExprents()!=null && firstData.getExprents().get(0).type == Exprent.EXPRENT_ASSIGNMENT) {
      firstDoExprent = (AssignmentExprent)firstData.getExprents().get(0);
    }
    lastData = getLastDirectData(stat.getFirst());
    if (lastData != null && lastData.getExprents() != null && !lastData.getExprents().isEmpty()) {
      lastExprent = lastData.getExprents().get(lastData.getExprents().size() - 1);
    }

    if (stat.getLoopType() == DoStatement.LoopType.WHILE && initExprents[0] != null && firstDoExprent != null) {
      if (tryConvertForEach(initExprents, stat, firstDoExprent, preData, firstData, lastData, lastExprent) ) {
        return true;
      }
    }

    //cleanEmptyStatements(stat, firstData); //TODO: Look into this and see what it does...

    return false;
  }

  /**
   * Attempts to convert a traditional loops into a foreach loop if the conditions are met.
   * Tries to find `next` and `hasNext` methods and check usual patterns
   *
   * @return true if the for loop is successfully converted into a foreach loop, false otherwise.
   */
  private static boolean tryConvertForEach(@Nullable AssignmentExprent @NotNull [] initExprents,
                                           @NotNull DoStatement stat,
                                           @NotNull AssignmentExprent firstDoExprent,
                                           @Nullable Statement preData,
                                           @Nullable Statement firstData,
                                           @Nullable Statement lastData,
                                           @Nullable Exprent lastExprent) {
    if (initExprents[0]!=null && isIteratorCall(initExprents[0].getRight())) {

      //Streams mimic Iterable but arnt.. so explicitly disallow their enhancements
      //TODO: Check inheritance for Iterable instead of just names?
      InvocationExprent invc = (InvocationExprent)getUncast((initExprents[0]).getRight());
      if (invc.getClassName().startsWith("java/util/stream")) {
        return false;
      }

      if (stat.getConditionExprent() != null && !isHasNextCall(drillNots(stat.getConditionExprent())) ||
          firstDoExprent.type != Exprent.EXPRENT_ASSIGNMENT) {
        return false;
      }

      if ((!isNextCall(firstDoExprent.getRight()) && !isNextUnboxing(firstDoExprent.getRight())) || firstDoExprent.getLeft().type != Exprent.EXPRENT_VAR) {
        return false;
      }

      InvocationExprent next = (InvocationExprent)getUncast(firstDoExprent.getRight());
      if (isNextUnboxing(next)){ next = (InvocationExprent)getUncast(next.getInstance());}
      if (stat.getConditionExprent() == null) return false;
      InvocationExprent hnext = (InvocationExprent)getUncast(drillNots(stat.getConditionExprent()));
      if (next.getInstance().type != Exprent.EXPRENT_VAR ||
          hnext.getInstance().type != Exprent.EXPRENT_VAR ||
          ((VarExprent)initExprents[0].getLeft()).isVarReferenced(stat, (VarExprent)next.getInstance(), (VarExprent)hnext.getInstance())) {
        return false;
      }

      InvocationExprent holder = (InvocationExprent)(initExprents[0]).getRight();

      initExprents[0].fillBytecodeRange(holder.getInstance().bytecode);
      holder.fillBytecodeRange(holder.getInstance().bytecode);
      firstDoExprent.fillBytecodeRange(firstDoExprent.getLeft().bytecode);
      firstDoExprent.getRight().fillBytecodeRange(firstDoExprent.getLeft().bytecode);
      if (stat.getIncExprent() != null) {
        stat.getIncExprent().fillBytecodeRange(holder.getInstance().bytecode);
      }
      if (stat.getInitExprent() != null) {
        stat.getInitExprent().fillBytecodeRange(firstDoExprent.getLeft().bytecode);
      }

      stat.setLoopType(DoStatement.LoopType.FOREACH);
      stat.setInitExprent(firstDoExprent.getLeft());
      stat.setIncExprent(holder.getInstance());
      if (preData == null || preData.getExprents() == null || firstData == null || firstData.getExprents() == null) return false;
      preData.getExprents().remove(initExprents[0]);
      firstData.getExprents().remove(firstDoExprent);

      if (initExprents[1] != null && initExprents[1].getLeft().type == Exprent.EXPRENT_VAR &&
          holder.getInstance().type == Exprent.EXPRENT_VAR) {
        VarExprent copy = (VarExprent)initExprents[1].getLeft();
        VarExprent inc = (VarExprent)holder.getInstance();
        if (copy.getIndex() == inc.getIndex() && copy.getVersion() == inc.getVersion() &&
            !inc.isVarReferenced(stat.getTopParent(), copy) && !isNextCall(initExprents[1].getRight())) {
          preData.getExprents().remove(initExprents[1]);
          initExprents[1].fillBytecodeRange(initExprents[1].getRight().bytecode);
          if (stat.getIncExprent() == null) return false;
          stat.getIncExprent().fillBytecodeRange(initExprents[1].getRight().bytecode);
          stat.setIncExprent(initExprents[1].getRight());
        }
      }

      return true;
    }
    else if (initExprents[1] != null) {
      if (firstDoExprent.getRight().type != Exprent.EXPRENT_ARRAY || firstDoExprent.getLeft().type != Exprent.EXPRENT_VAR) {
        return false;
      }

      if (lastExprent == null || lastExprent.type != Exprent.EXPRENT_FUNCTION) {
        return false;
      }

      if (initExprents[0] != null && initExprents[0].getRight().type != Exprent.EXPRENT_CONST ||
          initExprents[1].getRight().type != Exprent.EXPRENT_FUNCTION ||
          stat.getConditionExprent() != null && stat.getConditionExprent().type != Exprent.EXPRENT_FUNCTION) {
        return false;
      }

      //FunctionExprent funcCond  = (FunctionExprent)drillNots(stat.getConditionExprent()); //TODO: Verify this is counter < copy.length
      FunctionExprent funcRight = (FunctionExprent)initExprents[1].getRight();
      FunctionExprent funcInc   = (FunctionExprent)lastExprent;
      ArrayExprent    arr       = (ArrayExprent)firstDoExprent.getRight();
      int incType = funcInc.getFuncType();

      if (funcRight.getFuncType() != FunctionExprent.FUNCTION_ARRAY_LENGTH ||
          (incType != FunctionExprent.FUNCTION_PPI && incType != FunctionExprent.FUNCTION_IPP) ||
          arr.getIndex().type != Exprent.EXPRENT_VAR ||
          arr.getArray().type != Exprent.EXPRENT_VAR) {
        return false;
      }

      VarExprent index = (VarExprent)arr.getIndex();
      VarExprent array = (VarExprent)arr.getArray();
      VarExprent counter = (VarExprent)funcInc.getLstOperands().get(0);

      if (counter.getIndex() != index.getIndex() ||
          counter.getVersion() != index.getVersion()) {
        return false;
      }

      if (counter.isVarReferenced(stat.getFirst(), index)) {
        return false;
      }

      funcRight.getLstOperands().get(0).addBytecodeOffsets(initExprents[0].bytecode);
      funcRight.getLstOperands().get(0).addBytecodeOffsets(initExprents[1].bytecode);
      funcRight.getLstOperands().get(0).addBytecodeOffsets(lastExprent.bytecode);
      firstDoExprent.getLeft().addBytecodeOffsets(firstDoExprent.bytecode);
      firstDoExprent.getLeft().addBytecodeOffsets(initExprents[0].bytecode);

      stat.setLoopType(DoStatement.LoopType.FOREACH);
      stat.setInitExprent(firstDoExprent.getLeft());
      stat.setIncExprent(funcRight.getLstOperands().get(0));
      if (preData == null || preData.getExprents() == null ||
          firstData == null || firstData.getExprents() == null ||
          lastData == null || lastData.getExprents() == null) {
        return false;
      }
      preData.getExprents().remove(initExprents[0]);
      preData.getExprents().remove(initExprents[1]);
      firstData.getExprents().remove(firstDoExprent);
      lastData.getExprents().remove(lastExprent);

      if (initExprents[2] != null && initExprents[2].getLeft().type == Exprent.EXPRENT_VAR) {
        VarExprent copy = (VarExprent)initExprents[2].getLeft();
        if (copy.getIndex() == array.getIndex() && copy.getVersion() == array.getVersion()) {
          preData.getExprents().remove(initExprents[2]);
          initExprents[2].getRight().addBytecodeOffsets(initExprents[2].bytecode);
          Exprent exprent = stat.getIncExprent();
          if (exprent == null) return false;
          initExprents[2].getRight().addBytecodeOffsets(exprent.bytecode);
          stat.setIncExprent(initExprents[2].getRight());
        }
      }

      return true;
    }
    return false;
  }

  private static Exprent drillNots(Exprent exp) {
    while (true) {
      if (exp.type == Exprent.EXPRENT_FUNCTION) {
        FunctionExprent fun = (FunctionExprent)exp;
        if (fun.getFuncType() == FunctionExprent.FUNCTION_BOOL_NOT) {
          exp = fun.getLstOperands().get(0);
        }
        else if (fun.getFuncType() == FunctionExprent.FUNCTION_EQ ||
                 fun.getFuncType() == FunctionExprent.FUNCTION_NE) {
          return fun.getLstOperands().get(0);
        }
        else {
          return exp;
        }
      }
      else {
        return exp;
      }
    }
  }

  private static Statement getFirstDirectData(Statement stat) {
    if (stat.getExprents() != null && !stat.getExprents().isEmpty()) {
      return stat;
    }

    for (Statement tmp : stat.getStats()) {
      Statement ret = getFirstDirectData(tmp);
      if (ret != null) {
        return ret;
      }
    }
    return null;
  }

  private static Exprent getUncast(Exprent exp) {
    if (exp.type == Exprent.EXPRENT_FUNCTION) {
      FunctionExprent func = (FunctionExprent)exp;
      if (func.getFuncType() == FunctionExprent.FUNCTION_CAST) {
        return getUncast(func.getLstOperands().get(0));
      }
    }
    return exp;
  }

  private static InvocationExprent asInvocationExprent(Exprent exp) {
    exp = getUncast(exp);
    if (exp.type == Exprent.EXPRENT_INVOCATION) {
      return (InvocationExprent) exp;
    }
    return null;
  }

  private static boolean isIteratorCall(Exprent exp) {
    final InvocationExprent iexp = asInvocationExprent(exp);
    if (iexp == null) {
      return false;
    }

    final MethodDescriptor descriptor = iexp.getDescriptor();
    final String name = iexp.getName();
    if (!(("iterator".equals(name) || "listIterator".equals(name)) &&
          descriptor.params.length == 0)) {
      return false;
    }
    if (!DecompilerContext.getStructContext().instanceOf(descriptor.ret.getValue(), "java/util/Iterator")) {
      return false;
    }
    return true;
  }

  private static boolean isHasNextCall(Exprent exp) {
    final InvocationExprent iexp = asInvocationExprent(exp);
    if (iexp == null) {
      return false;
    }
    if (!"java/util/Iterator".equals(iexp.getClassName())) {
      return false;
    }
    if (!("hasNext".equals(iexp.getName()) && "()Z".equals(iexp.getStringDescriptor()))) {
      return false;
    }
    return true;
  }

  private static boolean isNextCall(Exprent exp) {
    final InvocationExprent iexp = asInvocationExprent(exp);
    if (iexp == null) {
      return false;
    }
    if (!"java/util/Iterator".equals(iexp.getClassName())) {
      return false;
    }
    if(!("next".equals(iexp.getName()) && "()Ljava/lang/Object;".equals(iexp.getStringDescriptor()))) {
      return false;
    }
    return true;
  }

  private static boolean isNextUnboxing(Exprent exprent) {
    Exprent exp = getUncast(exprent);
    if (exp.type != Exprent.EXPRENT_INVOCATION)
      return false;
    InvocationExprent inv = (InvocationExprent)exp;
    return inv.isUnboxingCall() && isNextCall(inv.getInstance());
  }

  /**
   * Convert `while` to `do-while`.
   * Simple synthetic example:<p>
   * before: <pre>
   * {@code
   * while(true){
   *   doSomething();
   *   if (i >= 10) {
   *     break;,
   *   }
   * }
   * }
   * </pre>
   * after:
   * <pre>
   * {@code
   * do {
   *   doSomething();
   * } while(i < 10);
   * }
   * </pre>
   */
  public static boolean makeDoWhileLoops(RootStatement root) {
    if (makeDoWhileRec(root)) {
      SequenceHelper.condenseSequences(root);
      return true;
    }
    return false;
  }

  private static boolean makeDoWhileRec(Statement stat) {
    boolean ret = false;

    for (Statement st : stat.getStats()) {
      ret |= makeDoWhileRec(st);
    }

    if (stat.type == Statement.StatementType.DO) {
      DoStatement dostat = (DoStatement)stat;
      if (dostat.getLoopType() == DoStatement.LoopType.DO) {
        matchDoWhile(dostat);
        ret |= dostat.getLoopType() != DoStatement.LoopType.DO;
      }
    }

    return ret;
  }

  private static boolean addContinueOrBreak(DoStatement stat, StatEdge ifedge) {
    Statement outer = stat.getParent();
    while (outer != null && outer.type != Statement.StatementType.SWITCH && outer.type != Statement.StatementType.DO) {
      outer = outer.getParent();
    }

    if (outer != null && (outer.type == Statement.StatementType.SWITCH || ((DoStatement)outer).getLoopType() != DoStatement.LoopType.DO)) {
      Statement parent = stat.getParent();
      if (parent.type != Statement.StatementType.SEQUENCE || parent.getStats().getLast().equals(stat)) {
        // need to insert a break or continue after the loop
        if (ifedge.getDestination().equals(outer)) {
          stat.addSuccessor(new StatEdge(StatEdge.EdgeType.CONTINUE, stat, ifedge.getDestination(), outer));
          return true;
        }
        else if (isDirectPath(outer, ifedge.getDestination())) {
          stat.addSuccessor(new StatEdge(StatEdge.EdgeType.BREAK, stat, ifedge.getDestination(), outer));
          return true;
        }
      }
    }

    return false;
  }
}
