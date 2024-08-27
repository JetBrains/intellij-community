// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.*;

public class PPandMMHelper {

  private boolean exprentReplaced;
  private VarProcessor varProc;
  private Map<VarVersionPair, VarVersionPair> remaps = new HashMap<>();

  public PPandMMHelper(VarProcessor varProc) {
    this.varProc = varProc;
  }

  public boolean findPPandMM(RootStatement root) {

    FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
    DirectGraph dgraph = flatthelper.buildDirectGraph(root);

    LinkedList<DirectNode> stack = new LinkedList<>();
    stack.add(dgraph.first);

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

    updateVersions(dgraph);

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

            VarType condtype = econd.getExprType();
            if (exprsEqual(left, econd) && (midlayer == null || midlayer.equals(condtype))) {
              FunctionExprent ret = new FunctionExprent(
                func.getFuncType() == FunctionExprent.FUNCTION_ADD ? FunctionExprent.FUNCTION_PPI : FunctionExprent.FUNCTION_MMI,
                econd, func.bytecode);
              ret.setImplicitType(condtype);

              exprentReplaced = true;

              if (!left.equals(econd)) {
                remaps.put(new VarVersionPair((VarExprent)left), new VarVersionPair((VarExprent)econd));
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
    return varProc.getVarOriginalIndex(v1.getIndex()) == varProc.getVarOriginalIndex(v2.getIndex())
            && Objects.equals(v1.getVarType(), v2.getVarType());
  }


  private void updateVersions(DirectGraph graph) {
    if (remaps.isEmpty()) return;
    graph.iterateExprents(new DirectGraph.ExprentIterator() {
      @Override
      public int processExprent(Exprent exprent) {
        List<Exprent> lst = exprent.getAllExprents(true);
        lst.add(exprent);

        for (Exprent expr : lst) {
          if (expr.type == Exprent.EXPRENT_VAR) {
            VarExprent var = (VarExprent)expr;
            VarVersionPair nvar = remaps.get(new VarVersionPair(var));
            if (nvar != null) {
              var.setIndex(nvar.var);
              var.setVersion(nvar.version);
            }
          }
        }

        return 0;
      }
    });
  }
}
