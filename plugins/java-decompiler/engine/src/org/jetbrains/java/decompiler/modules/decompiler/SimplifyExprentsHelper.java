// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.SSAConstructorSparseEx;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory.FastSparseSet;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.*;
import java.util.Map.Entry;

public class SimplifyExprentsHelper {
  @SuppressWarnings("SpellCheckingInspection") private static final MatchEngine class14Builder = new MatchEngine(
    "statement type:if iftype:if exprsize:-1\n" +
    " exprent position:head type:if\n" +
    "  exprent type:function functype:eq\n" +
    "   exprent type:field name:$fieldname$\n" +
    "   exprent type:constant consttype:null\n" +
    " statement type:basicblock\n" +
    "  exprent position:-1 type:assignment ret:$assignfield$\n" +
    "   exprent type:var index:$var$\n" +
    "   exprent type:field name:$fieldname$\n" +
    " statement type:sequence statsize:2\n" +
    "  statement type:trycatch\n" +
    "   statement type:basicblock exprsize:1\n" +
    "    exprent type:assignment\n" +
    "     exprent type:var index:$var$\n" +
    "     exprent type:invocation invclass:java/lang/Class signature:forName(Ljava/lang/String;)Ljava/lang/Class;\n" +
    "      exprent position:0 type:constant consttype:string constvalue:$classname$\n" +
    "   statement type:basicblock exprsize:1\n" +
    "    exprent type:exit exittype:throw\n" +
    "  statement type:basicblock exprsize:1\n" +
    "   exprent type:assignment\n" +
    "    exprent type:field name:$fieldname$ ret:$field$\n" +
    "    exprent type:var index:$var$");

  private final boolean firstInvocation;

  public SimplifyExprentsHelper(boolean firstInvocation) {
    this.firstInvocation = firstInvocation;
  }

  public boolean simplifyStackVarsStatement(Statement stat, Set<Integer> setReorderedIfs, SSAConstructorSparseEx ssa, StructClass cl) {
    boolean res = false;

    List<Exprent> expressions = stat.getExprents();
    if (expressions == null) {
      boolean processClass14 = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_CLASS_1_4);

      while (true) {
        boolean changed = false;

        for (Statement st : stat.getStats()) {
          res |= simplifyStackVarsStatement(st, setReorderedIfs, ssa, cl);

          // collapse composed if's
          if (changed = IfHelper.mergeIfs(st, setReorderedIfs)) {
            break;
          }

          // collapse iff ?: statement
          if (changed = buildIff(st, ssa)) {
            break;
          }

          // collapse inlined .class property in version 1.4 and before
          if (processClass14 && (changed = collapseInlinedClass14(st))) {
            break;
          }
        }

        res |= changed;

        if (!changed) {
          break;
        }
      }
    }
    else {
      res = simplifyStackVarsExprents(expressions, cl);
    }

    return res;
  }

  private boolean simplifyStackVarsExprents(List<Exprent> list, StructClass cl) {
    boolean res = false;

    int index = 0;
    while (index < list.size()) {
      Exprent current = list.get(index);

      Exprent ret = isSimpleConstructorInvocation(current);
      if (ret != null) {
        list.set(index, ret);
        res = true;
        continue;
      }

      // lambda expression (Java 8)
      ret = isLambda(current, cl);
      if (ret != null) {
        list.set(index, ret);
        res = true;
        continue;
      }

      // remove monitor exit
      if (isMonitorExit(current)) {
        list.remove(index);
        res = true;
        continue;
      }

      // trivial assignment of a stack variable
      if (isTrivialStackAssignment(current)) {
        list.remove(index);
        res = true;
        continue;
      }

      if (index == list.size() - 1) {
        break;
      }

      Exprent next = list.get(index + 1);

      // constructor invocation
      if (isConstructorInvocationRemote(list, index)) {
        list.remove(index);
        res = true;
        continue;
      }

      // remove getClass() invocation, which is part of a qualified new
      if (DecompilerContext.getOption(IFernflowerPreferences.REMOVE_GET_CLASS_NEW)) {
        if (isQualifiedNewGetClass(current, next)) {
          list.remove(index);
          res = true;
          continue;
        }
      }

      // direct initialization of an array
      int arrCount = isArrayInitializer(list, index);
      if (arrCount > 0) {
        for (int i = 0; i < arrCount; i++) {
          list.remove(index + 1);
        }
        res = true;
        continue;
      }

      // add array initializer expression
      if (addArrayInitializer(current, next)) {
        list.remove(index + 1);
        res = true;
        continue;
      }

      // integer ++expr and --expr  (except for vars!)
      Exprent func = isPPIorMMI(current);
      if (func != null) {
        list.set(index, func);
        res = true;
        continue;
      }

      // expr++ and expr--
      if (isIPPorIMM(current, next)) {
        list.remove(index + 1);
        res = true;
        continue;
      }

      // assignment on stack
      if (isStackAssignment(current, next)) {
        list.remove(index + 1);
        res = true;
        continue;
      }

      if (!firstInvocation && isStackAssignment2(current, next)) {
        list.remove(index + 1);
        res = true;
        continue;
      }

      index++;
    }

    return res;
  }

  private static boolean addArrayInitializer(Exprent first, Exprent second) {
    if (first.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent as = (AssignmentExprent)first;

      if (as.getRight().type == Exprent.EXPRENT_NEW && as.getLeft().type == Exprent.EXPRENT_VAR) {
        NewExprent newExpr = (NewExprent)as.getRight();

        if (!newExpr.getLstArrayElements().isEmpty()) {
          VarExprent arrVar = (VarExprent)as.getLeft();

          if (second.type == Exprent.EXPRENT_ASSIGNMENT) {
            AssignmentExprent aas = (AssignmentExprent)second;
            if (aas.getLeft().type == Exprent.EXPRENT_ARRAY) {
              ArrayExprent arrExpr = (ArrayExprent)aas.getLeft();
              if (arrExpr.getArray().type == Exprent.EXPRENT_VAR &&
                  arrVar.equals(arrExpr.getArray()) &&
                  arrExpr.getIndex().type == Exprent.EXPRENT_CONST) {
                int constValue = ((ConstExprent)arrExpr.getIndex()).getIntValue();

                if (constValue < newExpr.getLstArrayElements().size()) {
                  Exprent init = newExpr.getLstArrayElements().get(constValue);
                  if (init.type == Exprent.EXPRENT_CONST) {
                    ConstExprent cinit = (ConstExprent)init;
                    VarType arrType = newExpr.getNewType().decreaseArrayDim();
                    ConstExprent defaultVal = ExprProcessor.getDefaultArrayValue(arrType);

                    if (cinit.equals(defaultVal)) {
                      Exprent tempExpr = aas.getRight();

                      if (!tempExpr.containsExprent(arrVar)) {
                        newExpr.getLstArrayElements().set(constValue, tempExpr);

                        if (tempExpr.type == Exprent.EXPRENT_NEW) {
                          NewExprent tempNewExpr = (NewExprent)tempExpr;
                          int dims = newExpr.getNewType().arrayDim;
                          if (dims > 1 && !tempNewExpr.getLstArrayElements().isEmpty()) {
                            tempNewExpr.setDirectArrayInit(true);
                          }
                        }

                        return true;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    return false;
  }

  private static int isArrayInitializer(List<Exprent> list, int index) {
    Exprent current = list.get(index);
    if (current.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent as = (AssignmentExprent)current;

      if (as.getRight().type == Exprent.EXPRENT_NEW && as.getLeft().type == Exprent.EXPRENT_VAR) {
        NewExprent newExpr = (NewExprent)as.getRight();

        if (newExpr.getExprType().arrayDim > 0 && newExpr.getLstDims().size() == 1 && newExpr.getLstArrayElements().isEmpty() &&
            newExpr.getLstDims().get(0).type == Exprent.EXPRENT_CONST) {

          int size = (Integer)((ConstExprent)newExpr.getLstDims().get(0)).getValue();
          if (size == 0) {
            return 0;
          }

          VarExprent arrVar = (VarExprent)as.getLeft();
          Map<Integer, Exprent> mapInit = new HashMap<>();

          int i = 1;
          while (index + i < list.size() && i <= size) {
            boolean found = false;

            Exprent expr = list.get(index + i);
            if (expr.type == Exprent.EXPRENT_ASSIGNMENT) {
              AssignmentExprent aas = (AssignmentExprent)expr;
              if (aas.getLeft().type == Exprent.EXPRENT_ARRAY) {
                ArrayExprent arrExpr = (ArrayExprent)aas.getLeft();
                if (arrExpr.getArray().type == Exprent.EXPRENT_VAR && arrVar.equals(arrExpr.getArray()) &&
                    arrExpr.getIndex().type == Exprent.EXPRENT_CONST) {
                  // TODO: check for a number type. Failure extremely improbable, but nevertheless...
                  int constValue = ((ConstExprent)arrExpr.getIndex()).getIntValue();
                  if (constValue < size && !mapInit.containsKey(constValue)) {
                    if (!aas.getRight().containsExprent(arrVar)) {
                      mapInit.put(constValue, aas.getRight());
                      found = true;
                    }
                  }
                }
              }
            }

            if (!found) {
              break;
            }

            i++;
          }

          double fraction = ((double)mapInit.size()) / size;

          if ((arrVar.isStack() && fraction > 0) || (size <= 7 && fraction >= 0.3) || (size > 7 && fraction >= 0.7)) {
            List<Exprent> lstRet = new ArrayList<>();

            VarType arrayType = newExpr.getNewType().decreaseArrayDim();
            ConstExprent defaultVal = ExprProcessor.getDefaultArrayValue(arrayType);
            for (int j = 0; j < size; j++) {
              lstRet.add(defaultVal.copy());
            }

            int dims = newExpr.getNewType().arrayDim;
            for (Entry<Integer, Exprent> ent : mapInit.entrySet()) {
              Exprent tempExpr = ent.getValue();
              lstRet.set(ent.getKey(), tempExpr);

              if (tempExpr.type == Exprent.EXPRENT_NEW) {
                NewExprent tempNewExpr = (NewExprent)tempExpr;
                if (dims > 1 && !tempNewExpr.getLstArrayElements().isEmpty()) {
                  tempNewExpr.setDirectArrayInit(true);
                }
              }
            }

            newExpr.setLstArrayElements(lstRet);

            return mapInit.size();
          }
        }
      }
    }

    return 0;
  }

  private static boolean isTrivialStackAssignment(Exprent first) {
    if (first.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent asf = (AssignmentExprent)first;

      if (asf.getLeft().type == Exprent.EXPRENT_VAR && asf.getRight().type == Exprent.EXPRENT_VAR) {
        VarExprent left = (VarExprent)asf.getLeft();
        VarExprent right = (VarExprent)asf.getRight();
        return left.getIndex() == right.getIndex() && left.isStack() && right.isStack();
      }
    }

    return false;
  }

  private static boolean isStackAssignment2(Exprent first, Exprent second) {  // e.g. 1.4-style class invocation
    if (first.type == Exprent.EXPRENT_ASSIGNMENT && second.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent asf = (AssignmentExprent)first;
      AssignmentExprent ass = (AssignmentExprent)second;

      if (asf.getLeft().type == Exprent.EXPRENT_VAR && ass.getRight().type == Exprent.EXPRENT_VAR &&
          asf.getLeft().equals(ass.getRight()) && ((VarExprent)asf.getLeft()).isStack()) {
        if (ass.getLeft().type != Exprent.EXPRENT_VAR || !((VarExprent)ass.getLeft()).isStack()) {
          asf.setRight(new AssignmentExprent(ass.getLeft(), asf.getRight(), ass.bytecode));
          return true;
        }
      }
    }

    return false;
  }

  private static boolean isStackAssignment(Exprent first, Exprent second) {
    if (first.type == Exprent.EXPRENT_ASSIGNMENT && second.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent asf = (AssignmentExprent)first;
      AssignmentExprent ass = (AssignmentExprent)second;

      while (true) {
        if (asf.getRight().equals(ass.getRight())) {
          if ((asf.getLeft().type == Exprent.EXPRENT_VAR && ((VarExprent)asf.getLeft()).isStack()) &&
              (ass.getLeft().type != Exprent.EXPRENT_VAR || !((VarExprent)ass.getLeft()).isStack())) {

            if (!ass.getLeft().containsExprent(asf.getLeft())) {
              asf.setRight(ass);
              return true;
            }
          }
        }
        if (asf.getRight().type == Exprent.EXPRENT_ASSIGNMENT) {
          asf = (AssignmentExprent)asf.getRight();
        }
        else {
          break;
        }
      }
    }

    return false;
  }

  private static Exprent isPPIorMMI(Exprent first) {
    if (first.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent as = (AssignmentExprent)first;

      if (as.getRight().type == Exprent.EXPRENT_FUNCTION) {
        FunctionExprent func = (FunctionExprent)as.getRight();

        if (func.getFuncType() == FunctionExprent.FUNCTION_ADD || func.getFuncType() == FunctionExprent.FUNCTION_SUB) {
          Exprent econd = func.getLstOperands().get(0);
          Exprent econst = func.getLstOperands().get(1);

          if (econst.type != Exprent.EXPRENT_CONST && econd.type == Exprent.EXPRENT_CONST &&
              func.getFuncType() == FunctionExprent.FUNCTION_ADD) {
            econd = econst;
            econst = func.getLstOperands().get(0);
          }

          if (econst.type == Exprent.EXPRENT_CONST && ((ConstExprent)econst).hasValueOne()) {
            Exprent left = as.getLeft();

            if (left.type != Exprent.EXPRENT_VAR && left.equals(econd)) {
              int type = func.getFuncType() == FunctionExprent.FUNCTION_ADD ? FunctionExprent.FUNCTION_PPI : FunctionExprent.FUNCTION_MMI;
              FunctionExprent ret = new FunctionExprent(type, econd, func.bytecode);
              ret.setImplicitType(VarType.VARTYPE_INT);
              return ret;
            }
          }
        }
      }
    }

    return null;
  }

  private static boolean isIPPorIMM(Exprent first, Exprent second) {
    if (first.type == Exprent.EXPRENT_ASSIGNMENT && second.type == Exprent.EXPRENT_FUNCTION) {
      AssignmentExprent as = (AssignmentExprent)first;
      FunctionExprent in = (FunctionExprent)second;

      if ((in.getFuncType() == FunctionExprent.FUNCTION_MMI || in.getFuncType() == FunctionExprent.FUNCTION_PPI) &&
          in.getLstOperands().get(0).equals(as.getRight())) {

        if (in.getFuncType() == FunctionExprent.FUNCTION_MMI) {
          in.setFuncType(FunctionExprent.FUNCTION_IMM);
        }
        else {
          in.setFuncType(FunctionExprent.FUNCTION_IPP);
        }
        as.setRight(in);

        return true;
      }
    }

    return false;
  }

  private static boolean isMonitorExit(Exprent first) {
    if (first.type == Exprent.EXPRENT_MONITOR) {
      MonitorExprent expr = (MonitorExprent)first;
      return expr.getMonType() == MonitorExprent.MONITOR_EXIT &&
             expr.getValue().type == Exprent.EXPRENT_VAR &&
             !((VarExprent)expr.getValue()).isStack();
    }

    return false;
  }

  private static boolean isQualifiedNewGetClass(Exprent first, Exprent second) {
    if (first.type == Exprent.EXPRENT_INVOCATION) {
      InvocationExprent invocation = (InvocationExprent)first;

      if (!invocation.isStatic() && invocation.getInstance().type == Exprent.EXPRENT_VAR && invocation.getName().equals("getClass") &&
          invocation.getStringDescriptor().equals("()Ljava/lang/Class;")) {

        List<Exprent> lstExprents = second.getAllExprents();
        lstExprents.add(second);

        for (Exprent expr : lstExprents) {
          if (expr.type == Exprent.EXPRENT_NEW) {
            NewExprent newExpr = (NewExprent)expr;
            if (newExpr.getConstructor() != null && !newExpr.getConstructor().getLstParameters().isEmpty() &&
                newExpr.getConstructor().getLstParameters().get(0).equals(invocation.getInstance())) {

              String classname = newExpr.getNewType().value;
              ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(classname);
              if (node != null && node.type != ClassNode.CLASS_ROOT) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }

  // propagate (var = new X) forward to the <init> invocation
  private static boolean isConstructorInvocationRemote(List<Exprent> list, int index) {
    Exprent current = list.get(index);

    if (current.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent as = (AssignmentExprent)current;

      if (as.getLeft().type == Exprent.EXPRENT_VAR && as.getRight().type == Exprent.EXPRENT_NEW) {

        NewExprent newExpr = (NewExprent)as.getRight();
        VarType newType = newExpr.getNewType();
        VarVersionPair leftPair = new VarVersionPair((VarExprent)as.getLeft());

        if (newType.type == CodeConstants.TYPE_OBJECT && newType.arrayDim == 0 && newExpr.getConstructor() == null) {
          for (int i = index + 1; i < list.size(); i++) {
            Exprent remote = list.get(i);

            // <init> invocation
            if (remote.type == Exprent.EXPRENT_INVOCATION) {
              InvocationExprent in = (InvocationExprent)remote;

              if (in.getFunctype() == InvocationExprent.TYP_INIT &&
                  in.getInstance().type == Exprent.EXPRENT_VAR &&
                  as.getLeft().equals(in.getInstance())) {
                newExpr.setConstructor(in);
                in.setInstance(null);

                list.set(i, as.copy());

                return true;
              }
            }

            // check for variable in use
            Set<VarVersionPair> setVars = remote.getAllVariables();
            if (setVars.contains(leftPair)) { // variable used somewhere in between -> exit, need a better reduced code
              return false;
            }
          }
        }
      }
    }

    return false;
  }

  private static Exprent isLambda(Exprent exprent, StructClass cl) {
    List<Exprent> lst = exprent.getAllExprents();
    for (Exprent expr : lst) {
      Exprent ret = isLambda(expr, cl);
      if (ret != null) {
        exprent.replaceExprent(expr, ret);
      }
    }

    if (exprent.type == Exprent.EXPRENT_INVOCATION) {
      InvocationExprent in = (InvocationExprent)exprent;

      if (in.getInvocationTyp() == InvocationExprent.INVOKE_DYNAMIC) {
        String lambda_class_name = cl.qualifiedName + in.getInvokeDynamicClassSuffix();
        ClassNode lambda_class = DecompilerContext.getClassProcessor().getMapRootClasses().get(lambda_class_name);

        if (lambda_class != null) { // real lambda class found, replace invocation with an anonymous class
          NewExprent newExpr = new NewExprent(new VarType(lambda_class_name, true), null, 0, in.bytecode);
          newExpr.setConstructor(in);
          // note: we don't set the instance to null with in.setInstance(null) like it is done for a common constructor invocation
          // lambda can also be a reference to a virtual method (e.g. String x; ...(x::toString);)
          // in this case instance will hold the corresponding object

          return newExpr;
        }
      }
    }

    return null;
  }

  private static Exprent isSimpleConstructorInvocation(Exprent exprent) {
    List<Exprent> lst = exprent.getAllExprents();
    for (Exprent expr : lst) {
      Exprent ret = isSimpleConstructorInvocation(expr);
      if (ret != null) {
        exprent.replaceExprent(expr, ret);
      }
    }

    if (exprent.type == Exprent.EXPRENT_INVOCATION) {
      InvocationExprent in = (InvocationExprent)exprent;
      if (in.getFunctype() == InvocationExprent.TYP_INIT && in.getInstance().type == Exprent.EXPRENT_NEW) {
        NewExprent newExpr = (NewExprent)in.getInstance();
        newExpr.setConstructor(in);
        in.setInstance(null);
        return newExpr;
      }
    }

    return null;
  }

  private static boolean buildIff(Statement stat, SSAConstructorSparseEx ssa) {
    if (stat.type == Statement.TYPE_IF && stat.getExprents() == null) {
      IfStatement statement = (IfStatement)stat;
      Exprent ifHeadExpr = statement.getHeadexprent();
      Set<Integer> ifHeadExprBytecode = (ifHeadExpr == null ? null : ifHeadExpr.bytecode);

      if (statement.iftype == IfStatement.IFTYPE_IFELSE) {
        Statement ifStatement = statement.getIfstat();
        Statement elseStatement = statement.getElsestat();

        if (ifStatement.getExprents() != null && ifStatement.getExprents().size() == 1 &&
            elseStatement.getExprents() != null && elseStatement.getExprents().size() == 1 &&
            ifStatement.getAllSuccessorEdges().size() == 1 && elseStatement.getAllSuccessorEdges().size() == 1 &&
            ifStatement.getAllSuccessorEdges().get(0).getDestination() == elseStatement.getAllSuccessorEdges().get(0).getDestination()) {
          Exprent ifExpr = ifStatement.getExprents().get(0);
          Exprent elseExpr = elseStatement.getExprents().get(0);

          if (ifExpr.type == Exprent.EXPRENT_ASSIGNMENT && elseExpr.type == Exprent.EXPRENT_ASSIGNMENT) {
            AssignmentExprent ifAssign = (AssignmentExprent)ifExpr;
            AssignmentExprent elseAssign = (AssignmentExprent)elseExpr;

            if (ifAssign.getLeft().type == Exprent.EXPRENT_VAR && elseAssign.getLeft().type == Exprent.EXPRENT_VAR) {
              VarExprent ifVar = (VarExprent)ifAssign.getLeft();
              VarExprent elseVar = (VarExprent)elseAssign.getLeft();

              if (ifVar.getIndex() == elseVar.getIndex() && ifVar.isStack()) { // ifVar.getIndex() >= VarExprent.STACK_BASE) {
                boolean found = false;

                for (Entry<VarVersionPair, FastSparseSet<Integer>> ent : ssa.getPhi().entrySet()) {
                  if (ent.getKey().var == ifVar.getIndex()) {
                    if (ent.getValue().contains(ifVar.getVersion()) && ent.getValue().contains(elseVar.getVersion())) {
                      found = true;
                      break;
                    }
                  }
                }

                if (found) {
                  List<Exprent> data = new ArrayList<>(statement.getFirst().getExprents());

                  List<Exprent> operands = Arrays.asList(statement.getHeadexprent().getCondition(), ifAssign.getRight(), elseAssign.getRight());
                  data.add(new AssignmentExprent(ifVar, new FunctionExprent(FunctionExprent.FUNCTION_IIF, operands, ifHeadExprBytecode), ifHeadExprBytecode));
                  statement.setExprents(data);

                  if (statement.getAllSuccessorEdges().isEmpty()) {
                    StatEdge ifEdge = ifStatement.getAllSuccessorEdges().get(0);
                    StatEdge edge = new StatEdge(ifEdge.getType(), statement, ifEdge.getDestination());

                    statement.addSuccessor(edge);
                    if (ifEdge.closure != null) {
                      ifEdge.closure.addLabeledEdge(edge);
                    }
                  }

                  SequenceHelper.destroyAndFlattenStatement(statement);

                  return true;
                }
              }
            }
          }
          else if (ifExpr.type == Exprent.EXPRENT_EXIT && elseExpr.type == Exprent.EXPRENT_EXIT) {
            ExitExprent ifExit = (ExitExprent)ifExpr;
            ExitExprent elseExit = (ExitExprent)elseExpr;

            if (ifExit.getExitType() == elseExit.getExitType() && ifExit.getValue() != null && elseExit.getValue() != null &&
                ifExit.getExitType() == ExitExprent.EXIT_RETURN) {
              // throw is dangerous, because of implicit casting to a common superclass
              // e.g. throws IOException and throw true?new RuntimeException():new IOException(); won't work
              if (ifExit.getExitType() == ExitExprent.EXIT_THROW &&
                  !ifExit.getValue().getExprType().equals(elseExit.getValue().getExprType())) {  // note: getExprType unreliable at this point!
                return false;
              }

              // avoid flattening to 'iff' if any of the branches is an 'iff' already
              if (isIff(ifExit.getValue()) || isIff(elseExit.getValue())) {
                return false;
              }

              List<Exprent> data = new ArrayList<>(statement.getFirst().getExprents());

              data.add(new ExitExprent(ifExit.getExitType(), new FunctionExprent(FunctionExprent.FUNCTION_IIF,
                                                                               Arrays.asList(
                                                                                 statement.getHeadexprent().getCondition(),
                                                                                 ifExit.getValue(),
                                                                                 elseExit.getValue()), ifHeadExprBytecode), ifExit.getRetType(), ifHeadExprBytecode));
              statement.setExprents(data);

              StatEdge retEdge = ifStatement.getAllSuccessorEdges().get(0);
              Statement closure = retEdge.closure == statement ? statement.getParent() : retEdge.closure;
              statement.addSuccessor(new StatEdge(StatEdge.TYPE_BREAK, statement, retEdge.getDestination(), closure));

              SequenceHelper.destroyAndFlattenStatement(statement);

              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isIff(Exprent exp) {
    return exp.type == Exprent.EXPRENT_FUNCTION && ((FunctionExprent) exp).getFuncType() == FunctionExprent.FUNCTION_IIF;
  }

  private static boolean collapseInlinedClass14(Statement stat) {
    boolean ret = class14Builder.match(stat);
    if (ret) {
      String class_name = (String)class14Builder.getVariableValue("$classname$");
      AssignmentExprent assignment = (AssignmentExprent)class14Builder.getVariableValue("$assignfield$");
      FieldExprent fieldExpr = (FieldExprent)class14Builder.getVariableValue("$field$");

      assignment.replaceExprent(assignment.getRight(), new ConstExprent(VarType.VARTYPE_CLASS, class_name, null));

      List<Exprent> data = new ArrayList<>(stat.getFirst().getExprents());

      stat.setExprents(data);

      SequenceHelper.destroyAndFlattenStatement(stat);

      ClassWrapper wrapper = (ClassWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_WRAPPER);
      if (wrapper != null) {
        wrapper.getHiddenMembers().add(InterpreterUtil.makeUniqueKey(fieldExpr.getName(), fieldExpr.getDescriptor().descriptorString));
      }
    }

    return ret;
  }
}