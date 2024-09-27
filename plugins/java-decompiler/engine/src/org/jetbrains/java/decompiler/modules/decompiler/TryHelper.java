// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersion;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class TryHelper
{
  /**
   * Tries to convert `try` statement to `try-with-resource`
   */
  public static boolean enhanceTryStats(RootStatement root, StructMethod mt) {
    boolean ret = makeTryWithResourceRec(root, mt, root.getDummyExit(), new ArrayList<>());
    if (ret) {
      SequenceHelper.condenseSequences(root);
      if (collapseTryRec(root, mt)) {
        SequenceHelper.condenseSequences(root);
      }
    }
    return ret;
  }

  private static boolean makeTryWithResourceRec(Statement stat, StructMethod mt, DummyExitStatement exit, List<TryStatementJ11> stack) {
    boolean ret = false;
    if (stat.type == Statement.StatementType.CATCH_ALL && ((CatchAllStatement)stat).isFinally()) {
      if (makeTryWithResource((CatchAllStatement)stat)) {
        return true;
      }
    }

    if (mt.getBytecodeVersion() >= CodeConstants.BYTECODE_JAVA_11 && stat.type == Statement.StatementType.TRY_CATCH) {
      ret |= addTryWithResourceJ11((CatchStatement) stat, stack);
    }

    for (int i = 0; i < stat.getStats().size(); i++) {
      Statement st = stat.getStats().get(i);
      ret |= makeTryWithResourceRec(st, mt, exit, stack);
    }

    if (!stack.isEmpty() && stack.get(0).tryStatement() == stat) {
      makeTryStatementJ11(stack, exit);
    }

    return ret;
  }

  private static boolean collapseTryRec(Statement stat, StructMethod mt) {
    // This method uses two different algorithms as the J11 code expects to process everything that it can without returning, while the J8 expects to return early.
    // Future work could be done to improve the flow of things here.
    if (mt.getBytecodeVersion() >= CodeConstants.BYTECODE_JAVA_11) {
      boolean ret = false;
      if (stat.type == Statement.StatementType.TRY_CATCH) {
        CatchStatement tryStat = (CatchStatement) stat;
        if (collapseTryJ11(tryStat)) {
          ret = true;
        }
      }

      for (Statement st : stat.getStats()) {
        ret |= collapseTryRec(st, mt);
      }

      return ret;
    } else {
      if (stat.type == Statement.StatementType.TRY_CATCH) {
        CatchStatement tryStat = (CatchStatement) stat;
        if (collapseTry(tryStat)) {
          return true;
        }
      }

      for (Statement st : stat.getStats()) {
        if (collapseTryRec(st, mt)) {
          return true;
        }
      }

      return false;
    }
  }

  private static boolean makeTryWithResource(CatchAllStatement finallyStat) {
    Statement handler = finallyStat.getHandler();

    // The finally block has a specific statement structure we can check for
    if (handler.getStats().size() != 2) {
      return false;
    }

    Statement toCheck = finallyStat.getHandler().getFirst();
    if (toCheck.type != Statement.StatementType.IF || ((IfStatement)toCheck).getIfstat().type != Statement.StatementType.IF) {
      return false;
    }

    toCheck = ((IfStatement)toCheck).getIfstat();

    if (((IfStatement)toCheck).getElsestat() == null) {
      return false;
    }

    Statement elseBlock = ((IfStatement)toCheck).getElsestat();
    VarExprent var = null;

    if (elseBlock.getExprents() != null && elseBlock.getExprents().size() == 1) {
      Exprent exp = elseBlock.getExprents().get(0);

      if (isCloseable(exp)) {
        var = (VarExprent)((InvocationExprent)exp).getInstance();
      }
    }

    if (var != null) {
      AssignmentExprent ass = null;
      BasicBlockStatement initBlock = null;
      for (StatEdge edge : finallyStat.getAllPredecessorEdges()) {
        if (edge.getDestination().equals(finallyStat) && edge.getSource().type == Statement.StatementType.BASIC_BLOCK) {
          ass = findResourceDef(var, edge.getSource());
          if (ass != null) {
            initBlock = (BasicBlockStatement)edge.getSource();
            break;
          }
        }
      }

      if (ass != null) {
        Statement stat = finallyStat.getParent();
        Statement stat2 = finallyStat.getFirst();

        if (stat2.type == Statement.StatementType.TRY_CATCH) {
          CatchStatement child = (CatchStatement)stat2;

          AssignmentExprent resourceDef = (AssignmentExprent)ass.copy();
          if (ass.getRight().getExprType().equals(VarType.VARTYPE_NULL)) {
            if (child.getFirst() != null) {
              fixResourceAssignment(resourceDef, child.getFirst());
            }
          }

          if (resourceDef.getRight().getExprType().equals(VarType.VARTYPE_NULL)) {
            return false;
          }

          child.setTryType(CatchStatement.CatchStatementType.RESOURCES);
          List<Exprent> exprents = initBlock.getExprents();
          if (exprents != null) {
            exprents.remove(ass);
          }
          child.getResources().add(0, resourceDef);

          if (!finallyStat.getVarDefinitions().isEmpty()) {
            child.getVarDefinitions().addAll(0, finallyStat.getVarDefinitions());
          }

          stat.replaceStatement(finallyStat, child);
          removeRedundantThrow(initBlock, child);
          return true;
        }
      }
    }

    return false;
  }

  // Make try with resources with the new style bytecode (J11+)
  // It doesn't use finally blocks, and is just a try catch
  public static boolean addTryWithResourceJ11(CatchStatement tryStatement, List<TryStatementJ11> stack) {
    // Doesn't have a catch block, probably already processed
    if (tryStatement.getStats().size() < 2) {
      return false;
    }

    if (!tryStatement.getVars().get(0).getVarType().getValue().equals("java/lang/Throwable")) {
      return false;
    }

    Statement inner = tryStatement.getStats().get(1); // Get catch block

    VarExprent closeable = null;

    boolean nullable = false;

    if (inner instanceof SequenceStatement) {
      // Replace dummy inner with real inner
      inner = inner.getStats().get(0);

      // If the catch statement contains a simple try catch, then it's a nonnull resource
      if (inner instanceof CatchStatement) {
        if (inner.getStats().isEmpty()) {
          return false;
        }

        Statement inTry = inner.getStats().get(0);

        // Catch block contains a basic block inside which has the closeable invocation
        if (inTry instanceof BasicBlockStatement && inTry.getExprents()!=null && !inTry.getExprents().isEmpty()) {
          Exprent first = inTry.getExprents().get(0);

          if (isCloseable(first)) {
            closeable = (VarExprent) ((InvocationExprent)first).getInstance();
          }
        }
      }

      // Nullable resource, contains null checks
      if (inner instanceof IfStatement) {
        Exprent ifCase = ((IfStatement)inner).getHeadexprent().getCondition();

        if (ifCase instanceof FunctionExprent) {
          // Will look like "if (!(!(var != null)))"
          FunctionExprent func = unwrapNegations((FunctionExprent) ifCase);

          Exprent check = func.getLstOperands().get(0);

          // If it's not a var, end processing early
          if (!(check instanceof VarExprent)) {
            return false;
          }

          // Make sure it's checking against null
          if (func.getLstOperands().get(1).getExprType().equals(VarType.VARTYPE_NULL)) {
            // Ensured that the if stat is a null check

            inner = ((IfStatement)inner).getIfstat();

            if (inner == null) {
              return false;
            }

            // Process try catch inside of if statement
            if (inner instanceof CatchStatement && !inner.getStats().isEmpty()) {
              Statement inTry = inner.getStats().get(0);

              if (inTry instanceof BasicBlockStatement && inTry.getExprents()!=null && !inTry.getExprents().isEmpty()) {
                Exprent first = inTry.getExprents().get(0);

                // Check for closable invocation
                if (isCloseable(first)) {
                  closeable = (VarExprent) ((InvocationExprent)first).getInstance();
                  nullable = true;

                  // Double check that the variables in the null check and the closeable match
                  if (!closeable.getVarVersion().equals(((VarExprent)check).getVarVersion())) {
                    closeable = null;
                  }
                }
              }
            }
          }
        }
      }
    }

    // Didn't find an autocloseable, return early
    if (closeable == null) {
      return false;
    }

    Set<Statement> destinations = findExitpoints(tryStatement);
    if (destinations.isEmpty()) {
      return false;
    }

    Statement check = tryStatement;
    List<StatEdge> preds = new ArrayList<>();
    while (check != null && preds.isEmpty()) {
      preds = check.getPredecessorEdges(StatEdge.EdgeType.REGULAR);
      check = check.getParent();
    }

    if (preds.isEmpty()) {
      return false;
    }

    StatEdge edge = preds.get(0);
    if (edge.getSource() instanceof BasicBlockStatement) {
      AssignmentExprent assignment = findResourceDef(closeable, edge.getSource());

      if (assignment == null) {
        return false;
      }

      for (Statement destination : destinations) {
        if (!isValid(destination, closeable, nullable)) {
          return false;
        }
      }

      stack.add(new TryStatementJ11(destinations, closeable, nullable, assignment, edge, tryStatement));
      return true;
    }

    return false;
  }

  private static void makeTryStatementJ11(List<TryStatementJ11> tryStatements, DummyExitStatement exit) {
    if(tryStatements.isEmpty()) return;
    TryStatementJ11 tryStatementRecord = tryStatements.get(0);
    Set<Statement> destinations = tryStatementRecord.destinations();
    boolean nullable = tryStatementRecord.nullable();
    AssignmentExprent assignment = tryStatementRecord.assignment();
    StatEdge edge = tryStatementRecord.pred();
    CatchStatement tryStatement = tryStatementRecord.tryStatement();

    for (Statement destination : destinations) {
      removeTempAssignments(destination, tryStatements, exit);
    }

    for (Statement destination : destinations) {
      removeClose(destination, nullable, exit);
    }

    List<Exprent> exprents = edge.getSource().getExprents();
    if (exprents != null) {
      exprents.remove(assignment);
    }

    // Add resource assignment to try
    tryStatement.getResources().add(0, assignment);

    // Get catch block
    Statement remove = tryStatement.getStats().get(1);

    // Flatten references to statement
    SequenceHelper.destroyAndFlattenStatement(remove);

    // Destroy catch block
    tryStatement.getStats().remove(1);

    tryStatement.setTryType(CatchStatement.CatchStatementType.RESOURCES);
    tryStatements.remove(0);
  }

  private static boolean isValid(Statement stat, VarExprent closeable, boolean nullable) {
    if (nullable) {
      // Check for if statement that contains a null check and a close()
      if (stat instanceof IfStatement ifStat) {
        Exprent condition = ifStat.getHeadexprent().getCondition();

        if (condition instanceof FunctionExprent) {
          // This can sometimes be double inverted negative conditions too, handle that case
          FunctionExprent func = unwrapNegations((FunctionExprent) condition);

          // Ensure the exprent is the one we want to remove
          if (func.getFuncType() == FunctionExprent.FUNCTION_NE && func.getLstOperands().get(0) instanceof VarExprent && func.getLstOperands().get(1).getExprType().equals(VarType.VARTYPE_NULL)) {
            if (func.getLstOperands().get(0) instanceof VarExprent && ((VarExprent) func.getLstOperands().get(0)).getVarVersion().equals(closeable.getVarVersion())) {
              return true;
            }
          }
        }
      }
    } else {
      if (stat instanceof BasicBlockStatement) {
        if (stat.getExprents() != null && !stat.getExprents().isEmpty()) {
          Exprent exprent = stat.getExprents().get(0);

          if (exprent instanceof InvocationExprent) {
            Exprent inst = ((InvocationExprent) exprent).getInstance();

            // Ensure the var exprent we want to remove is the right one
            if (inst instanceof VarExprent && inst.equals(closeable) && isCloseable(exprent)) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private static void removeTempAssignments(Statement statement, List<TryStatementJ11> stack, DummyExitStatement exitStat) {
    Statement stat = statement;
    int exprentIndex = 0;
    boolean previousNonNullable = false;
    for (int i = stack.size() - 1; i >= 0; i--) {
      TryStatementJ11 tryStatement = stack.get(i);
      if (tryStatement.nullable()) {
        if (stat.getAllSuccessorEdges().size() == 1) {
          stat = stat.getAllSuccessorEdges().get(0).getDestination();
          exprentIndex = 0;
          if (previousNonNullable) {
            if (stat.getAllSuccessorEdges().size() == 1) {
              stat = stat.getAllSuccessorEdges().get(0).getDestination();
            }
            previousNonNullable = false;
          }
        }
      } else {
        exprentIndex++;
        previousNonNullable = true;
      }
    }

    List<StatEdge> edges = stat.getAllPredecessorEdges();
    edges.removeIf((edge) -> edge.getSource() instanceof CatchAllStatement catchAll && catchAll.isFinally());
    if (exprentIndex == 0 && edges.size() > 2) {
      return;
    }

    ExitExprent exit = null;
    Runnable remove = null;
    if (stat.getExprents() != null && stat.getExprents().size() > exprentIndex) {
      Exprent exp = stat.getExprents().get(exprentIndex);
      if (exp.type == Exprent.EXPRENT_EXIT) {
        exit = (ExitExprent) exp;
        final Statement finalStat = stat;
        final int finalIndex = exprentIndex;
        remove = () -> {
          finalStat.getExprents().remove(finalIndex);
          if (finalStat.getExprents().isEmpty()) {
            addEnd(finalStat, exitStat);
          }
        };
      }
    }

    if (exit != null && exit.getValue() != null && exit.getValue().type == Exprent.EXPRENT_VAR) {
      VarVersion returnVar = ((VarExprent) exit.getValue()).getVarVersion();
      if (!statement.getAllPredecessorEdges().isEmpty()) {
        StatEdge edge = statement.getAllPredecessorEdges().get(0);
        Statement ret = edge.getSource();
        if (ret.type == Statement.StatementType.BASIC_BLOCK && ret.getExprents() != null && !ret.getExprents().isEmpty()) {
          Exprent last = ret.getExprents().get(ret.getExprents().size() - 1);
          if (last.type == Exprent.EXPRENT_ASSIGNMENT) {
            AssignmentExprent assignment = (AssignmentExprent) last;
            if (assignment.getLeft().type == Exprent.EXPRENT_VAR) {
              VarVersion assigned = ((VarExprent) assignment.getLeft()).getVarVersion();
              if (returnVar.var == assigned.var) {
                exit.replaceExprent(exit.getValue(), assignment.getRight());
                ret.getExprents().set(ret.getExprents().size() - 1, exit);
                remove.run();
              }
            }
          }
        }
      }
    }
  }

  private static void removeClose(Statement statement, boolean nullable, DummyExitStatement exit) {
    if (nullable) {
      // Breaking out of parent, remove label
      List<StatEdge> edges = statement.getAllSuccessorEdges();
      if (!edges.isEmpty() && edges.get(0).closure == statement.getParent()) {
        SequenceHelper.destroyAndFlattenStatement(statement);
      } else {
        for (StatEdge edge : statement.getFirst().getAllSuccessorEdges()) {
          edge.getDestination().removePredecessor(edge);
        }

        for (StatEdge edge : ((IfStatement)statement).getIfstat().getAllSuccessorEdges()) {
          edge.getDestination().removePredecessor(edge);

          if (edge.closure != null) {
            edge.closure.getLabelEdges().remove(edge);
          }
        }

        if (!statement.getNeighboursSet(StatEdge.EdgeType.ALL, StatEdge.EdgeDirection.FORWARD).contains(exit)) {
          // Keep the label as it's not the parent
          BasicBlockStatement newStat = new BasicBlockStatement(new BasicBlock(
              DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));

          newStat.setExprents(new ArrayList<>());
          statement.getParent().replaceStatement(statement, newStat);
        } else {
          addEnd(statement, exit);
        }
      }
    } else {
      List<Exprent> exprents = statement.getExprents();
      if(exprents==null) return;
      exprents.remove(0);
      if (exprents.isEmpty() && statement.getNeighboursSet(StatEdge.EdgeType.ALL, StatEdge.EdgeDirection.FORWARD).contains(exit)) {
        addEnd(statement, exit);
      }
    }
  }

  private static Set<Statement> findExitpoints(Statement stat) {
    Set<StatEdge> edges = new LinkedHashSet<>();
    findEdgesLeaving(stat.getFirst(), stat, edges);

    return edges.stream().map(StatEdge::getDestination).collect(Collectors.toSet());
  }

  private static void findEdgesLeaving(Statement curr, Statement check, Set<StatEdge> edges) {
    findEdgesLeaving(curr, check, edges, false);
  }

  public static void findEdgesLeaving(Statement curr, Statement check, Set<StatEdge> edges, boolean allowExit) {
    for (StatEdge edge : curr.getAllSuccessorEdges()) {
      if (!check.containsStatement(edge.getDestination()) && (allowExit || !(edge.getDestination() instanceof DummyExitStatement))) {
        edges.add(edge);
      }
    }

    for (Statement stat : curr.getStats()) {
      findEdgesLeaving(stat, check, edges, allowExit);
    }
  }

  private static FunctionExprent unwrapNegations(FunctionExprent func) {
    while (func.getFuncType() == FunctionExprent.FUNCTION_BOOL_NOT) {
      Exprent expr = func.getLstOperands().get(0);

      if (expr instanceof FunctionExprent) {
        func = (FunctionExprent) expr;
      } else {
        break;
      }
    }

    return func;
  }

  private static boolean collapseTry(CatchStatement catchStat) {
    Statement parent = catchStat;
    if (parent.getFirst() != null && parent.getFirst().type == Statement.StatementType.SEQUENCE) {
      parent = parent.getFirst();
    }
    if (parent != null && parent.getFirst() != null && parent.getFirst().type == Statement.StatementType.TRY_CATCH) {
      CatchStatement toRemove = (CatchStatement)parent.getFirst();

      if (toRemove.getTryType() == CatchStatement.CatchStatementType.RESOURCES) {
        catchStat.setTryType(CatchStatement.CatchStatementType.RESOURCES);
        catchStat.getResources().addAll(toRemove.getResources());

        catchStat.getVarDefinitions().addAll(toRemove.getVarDefinitions());
        parent.replaceStatement(toRemove, toRemove.getFirst());

        if (!toRemove.getVars().isEmpty()) {
          for (int i = 0; i < toRemove.getVars().size(); ++i) {
            catchStat.getVars().add(i, toRemove.getVars().get(i));
            catchStat.getExctStrings().add(i, toRemove.getExctStrings().get(i));

            catchStat.getStats().add(i + 1, catchStat.getStats().get(i + 1));
          }
        }
        return true;
      }
    }
    return false;
  }

  // J11+
  // Merges try with resource statements that are nested within each other, as well as try with resources statements nested in a normal try.
  private static boolean collapseTryJ11(CatchStatement stat) {
    if (stat.getStats().isEmpty()) {
      return false;
    }

    boolean ret = false;
    boolean merged;
    do {
      merged = false;
      // Get the statement inside of the current try
      Statement inner = stat.getStats().get(0);
  
      // Check if the inner statement is a try statement
      if (inner instanceof CatchStatement) {
        // Filter on try with resources statements
        List<Exprent> resources = ((CatchStatement) inner).getResources();
        if (!resources.isEmpty()) {
          // One try inside of the catch
  
          // Only merge trys that have an inner statement size of 1, a single block
          // TODO: how does this handle nested nullable try stats?
          if (inner.getStats().size() == 1) {
            stat.setTryType(CatchStatement.CatchStatementType.RESOURCES);
            // Set the outer try to be resources, and initialize
            stat.getResources().addAll(resources);
            stat.getVarDefinitions().addAll(inner.getVarDefinitions());
  
            // Get inner block of inner try stat
            Statement innerBlock = inner.getStats().get(0);
  
            // Remove successors as the replaceStatement call will add the appropriate successor
            List<StatEdge> innerEdges = inner.getAllSuccessorEdges();
            for (StatEdge succ : innerBlock.getAllSuccessorEdges()) {
              boolean found = false;
              for (StatEdge innerEdge : innerEdges) {
                if (succ.getDestination() == innerEdge.getDestination() && succ.getType() == innerEdge.getType()) {
                  found = true;
                  break;
                }
              }
  
              if (found) {
                innerBlock.removeSuccessor(succ);
              }
            }
  
            // Replace the inner try statement with the block inside
            stat.replaceStatement(inner, innerBlock);
  
            ret = true;
            merged = true;
          }
        }
      }
    } while (merged);

    return ret;
  }

  private static AssignmentExprent findResourceDef(VarExprent var, Statement prevStatement) {
    List<Exprent> exprents = prevStatement.getExprents();
    if (exprents == null) return null;
    for (Exprent exp : exprents) {
      if (exp.type == Exprent.EXPRENT_ASSIGNMENT) {
        AssignmentExprent ass = (AssignmentExprent)exp;
        if (ass.getLeft().type == Exprent.EXPRENT_VAR) { // cannot use equals as var's varType may be unknown and not match
          VarExprent left = (VarExprent)ass.getLeft();
          if (left.getVarVersion().equals(var.getVarVersion())) {
            return ass;
          }
        }
      }
    }

    return null;
  }

  private static boolean isCloseable(Exprent exp) {
    if (exp.type == Exprent.EXPRENT_INVOCATION) {
      InvocationExprent invocExp = (InvocationExprent)exp;
      if (invocExp.getName().equals("close") && invocExp.getStringDescriptor().equals("()V")) {
        if (invocExp.getInstance() != null && invocExp.getInstance().type == Exprent.EXPRENT_VAR) {
          if (!DecompilerContext.getOption(IFernflowerPreferences.CHECK_CLOSABLE_INTERFACE)) return true;
          return DecompilerContext.getStructContext().instanceOf(invocExp.getClassName(), "java/lang/AutoCloseable");
        }
      }
    }

    return false;
  }

  private static void fixResourceAssignment(AssignmentExprent ass, Statement statement) {
    if (statement.getExprents() != null) {
      for (Exprent exp : statement.getExprents()) {
        if (exp.type == Exprent.EXPRENT_ASSIGNMENT) {
          AssignmentExprent toRemove = (AssignmentExprent)exp;
          if (ass.getLeft().equals(toRemove.getLeft()) && !toRemove.getRight().getExprType().equals(VarType.VARTYPE_NULL)) {
            ass.setRight(toRemove.getRight());
            statement.getExprents().remove(toRemove);
            break;
          }
        }
      }
    }
  }

  private static void removeRedundantThrow(BasicBlockStatement initBlock, CatchStatement catchStat) {
    if (catchStat.getStats().size() > 1) {
      boolean removed = false;
      Statement temp = null;
      int i = 1;
      for (; i < catchStat.getStats().size(); ++i) {
        temp = catchStat.getStats().get(i);

        if (temp.type == Statement.StatementType.BASIC_BLOCK && temp.getExprents() != null) {
          if (temp.getExprents().size() >= 2 && catchStat.getVars().get(i - 1).getVarType().getValue().equals("java/lang/Throwable")) {
            if (temp.getExprents().get(temp.getExprents().size() - 1).type == Exprent.EXPRENT_EXIT) {
              ExitExprent exitExprent = (ExitExprent)temp.getExprents().get(temp.getExprents().size() - 1);
              if (exitExprent.getExitType() == ExitExprent.EXIT_THROW && exitExprent.getValue().equals(catchStat.getVars().get(i - 1))) {

                catchStat.getExctStrings().remove(i - 1);
                catchStat.getVars().remove(i - 1);
                catchStat.getStats().remove(i);

                for (StatEdge edge : temp.getAllPredecessorEdges()) {
                  edge.getSource().removeSuccessor(edge);
                }

                for (StatEdge edge : temp.getAllSuccessorEdges()) {
                  edge.getDestination().removePredecessor(edge);
                }

                removed = true;
                break;
              }
            }
          }
        }
      }

      if (removed && temp.getExprents().get(temp.getExprents().size() - 2).type == Exprent.EXPRENT_ASSIGNMENT) {
        AssignmentExprent assignmentExp = (AssignmentExprent)temp.getExprents().get(temp.getExprents().size() - 2);
        if (assignmentExp.getLeft().getExprType().getValue().equals("java/lang/Throwable")) {
          List<Exprent> exprents = initBlock.getExprents();
          if (exprents == null) return;
          for (Exprent exprent : exprents) {
            if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
              AssignmentExprent toRemove = (AssignmentExprent)exprent;
              if (toRemove.getLeft().equals(assignmentExp.getLeft())) {
                initBlock.getExprents().remove(toRemove);
                return;
              }
            }
          }
        }
      }
    }
  }
  
  private static void addEnd(Statement stat, DummyExitStatement exit) {
    for (StatEdge edge : stat.getAllPredecessorEdges()) {
      edge.getSource().changeEdgeNode(StatEdge.EdgeDirection.FORWARD, edge, exit);
      exit.addPredecessor(edge);
      if (edge.closure != null) {
        edge.closure.getLabelEdges().remove(edge);
      }
    }
    
    for (StatEdge edge : stat.getAllSuccessorEdges()) {
      edge.getDestination().removePredecessor(edge);
    }
    stat.getParent().getStats().removeWithKey(stat.id);
  }
  
  public record TryStatementJ11(Set<Statement> destinations, VarExprent closeable, boolean nullable, AssignmentExprent assignment, StatEdge pred, CatchStatement tryStatement) {
    
  }
}
