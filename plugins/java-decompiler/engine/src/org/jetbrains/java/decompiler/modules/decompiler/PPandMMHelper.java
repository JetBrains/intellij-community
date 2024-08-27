// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class PPandMMHelper {

  private boolean exprentReplaced;
  private VarProcessor varProc;
  private DirectGraph dgraph;

  public PPandMMHelper(VarProcessor varProc) {
    this.varProc = varProc;
  }

  public boolean findPPandMM(RootStatement root) {

    FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
    this.dgraph = flatthelper.buildDirectGraph(root);

    LinkedList<DirectNode> stack = new LinkedList<>();
    stack.add(this.dgraph.first);

    HashSet<DirectNode> setVisited = new HashSet<>();

    boolean res = false;

    while (!stack.isEmpty()) {

      DirectNode node = stack.removeFirst();

      if (setVisited.contains(node)) {
        continue;
      }
      setVisited.add(node);

      res |= processExprentList(node.exprents);

      stack.addAll(node.successors);
    }

    return res;
  }

  private boolean processExprentList(List<Exprent> lst) {

    boolean result = false;

    for (int i = 0; i < lst.size(); i++) {
      Exprent exprent = lst.get(i);
      exprentReplaced = false;

      Exprent retexpr = processExprentRecursive(exprent);
      if (retexpr != null) {
        lst.set(i, retexpr);

        result = true;
        i--; // process the same exprent again
      }

      result |= exprentReplaced;
    }

    return result;
  }

  private Exprent processExprentRecursive(Exprent exprent) {

    boolean replaced = true;
    while (replaced) {
      replaced = false;

      for (Exprent expr : exprent.getAllExprents()) {
        Exprent retexpr = processExprentRecursive(expr);
        if (retexpr != null) {
          exprent.replaceExprent(expr, retexpr);
          replaced = true;
          exprentReplaced = true;
          break;
        }
      }
    }

    if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
      AssignmentExprent as = (AssignmentExprent)exprent;

      if (as.getRight().type == Exprent.EXPRENT_FUNCTION) {
        FunctionExprent func = (FunctionExprent)as.getRight();

        VarType midlayer = null;
        if (func.getFuncType() >= FunctionExprent.FUNCTION_I2L &&
            func.getFuncType() <= FunctionExprent.FUNCTION_I2S) {
          midlayer = func.getSimpleCastType();
          if (func.getLstOperands().get(0).type == Exprent.EXPRENT_FUNCTION) {
            func = (FunctionExprent)func.getLstOperands().get(0);
          }
          else {
            return null;
          }
        }

        if (func.getFuncType() == FunctionExprent.FUNCTION_ADD ||
            func.getFuncType() == FunctionExprent.FUNCTION_SUB) {
          Exprent econd = func.getLstOperands().get(0);
          Exprent econst = func.getLstOperands().get(1);

          if (econst.type != Exprent.EXPRENT_CONST && econd.type == Exprent.EXPRENT_CONST &&
              func.getFuncType() == FunctionExprent.FUNCTION_ADD) {
            econd = econst;
            econst = func.getLstOperands().get(0);
          }

          if (econst.type == Exprent.EXPRENT_CONST && ((ConstExprent)econst).hasValueOne()) {
            Exprent left = as.getLeft();

            VarType condtype = left.getExprType();
            if (exprsEqual(left, econd) && (midlayer == null || midlayer.equals(condtype))) {
              FunctionExprent ret = new FunctionExprent(
                func.getFuncType() == FunctionExprent.FUNCTION_ADD ? FunctionExprent.FUNCTION_PPI : FunctionExprent.FUNCTION_MMI,
                econd, func.bytecode);
              ret.setImplicitType(condtype);

              exprentReplaced = true;

              if (!left.equals(econd)) {
                updateVersions(this.dgraph, new VarVersionPair((VarExprent)left), new VarVersionPair((VarExprent)econd));
              }

              return ret;
            }
          }
        }
      }
    }

    return null;
  }

  private boolean exprsEqual(Exprent e1, Exprent e2) {
    if (e1 == e2) return true;
    if (e1 == null || e2 == null) return false;
    if (e1.type == Exprent.EXPRENT_VAR) {
      return varsEqual(e1, e2);
    }
    return e1.equals(e2);
  }

  private boolean varsEqual(Exprent e1, Exprent e2) {
    if (!(e1 instanceof VarExprent)) return false;
    if (!(e2 instanceof VarExprent)) return false;

    VarExprent v1 = (VarExprent)e1;
    VarExprent v2 = (VarExprent)e2;
    return varProc.getVarOriginalIndex(v1.getIndex()) == varProc.getVarOriginalIndex(v2.getIndex());
    // TODO: Verify the types are in the same 'family' {byte->short->int}
    //        && Objects.equals(v1.getVarType(), v2.getVarType());
  }


  private void updateVersions(DirectGraph graph, final VarVersionPair oldVVP, final VarVersionPair newVVP) {
    graph.iterateExprents(new DirectGraph.ExprentIterator() {
      @Override
      public int processExprent(Exprent exprent) {
        List<Exprent> lst = exprent.getAllExprents(true);
        lst.add(exprent);

        for (Exprent expr : lst) {
          if (expr.type == Exprent.EXPRENT_VAR) {
            VarExprent var = (VarExprent)expr;
            if (var.getIndex() == oldVVP.var && var.getVersion() == oldVVP.version) {
              var.setIndex(newVVP.var);
              var.setVersion(newVVP.version);
            }
          }
        }

        return 0;
      }
    });
  }
}
