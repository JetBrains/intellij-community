/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class VarTypeProcessor {
  public static final int VAR_NON_FINAL = 1;
  public static final int VAR_EXPLICIT_FINAL = 2;
  public static final int VAR_FINAL = 3;

  private final StructMethod method;
  private final MethodDescriptor methodDescriptor;
  private final Map<VarVersionPair, VarType> mapExprentMinTypes = new HashMap<>();
  private final Map<VarVersionPair, VarType> mapExprentMaxTypes = new HashMap<>();
  private final Map<VarVersionPair, Integer> mapFinalVars = new HashMap<>();

  public VarTypeProcessor(StructMethod mt, MethodDescriptor md) {
    method = mt;
    methodDescriptor = md;
  }

  public void calculateVarTypes(RootStatement root, DirectGraph graph) {
    setInitVars(root);

    resetExprentTypes(graph);

    //noinspection StatementWithEmptyBody
    while (!processVarTypes(graph)) ;
  }

  private void setInitVars(RootStatement root) {
    boolean thisVar = !method.hasModifier(CodeConstants.ACC_STATIC);

    MethodDescriptor md = methodDescriptor;

    if (thisVar) {
      StructClass cl = (StructClass)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS);
      VarType clType = new VarType(CodeConstants.TYPE_OBJECT, 0, cl.qualifiedName);
      mapExprentMinTypes.put(new VarVersionPair(0, 1), clType);
      mapExprentMaxTypes.put(new VarVersionPair(0, 1), clType);
    }

    int varIndex = 0;
    for (int i = 0; i < md.params.length; i++) {
      mapExprentMinTypes.put(new VarVersionPair(varIndex + (thisVar ? 1 : 0), 1), md.params[i]);
      mapExprentMaxTypes.put(new VarVersionPair(varIndex + (thisVar ? 1 : 0), 1), md.params[i]);
      varIndex += md.params[i].stackSize;
    }

    // catch variables
    LinkedList<Statement> stack = new LinkedList<>();
    stack.add(root);

    while (!stack.isEmpty()) {
      Statement stat = stack.removeFirst();

      List<VarExprent> lstVars = null;
      if (stat.type == Statement.TYPE_CATCHALL) {
        lstVars = ((CatchAllStatement)stat).getVars();
      }
      else if (stat.type == Statement.TYPE_TRYCATCH) {
        lstVars = ((CatchStatement)stat).getVars();
      }

      if (lstVars != null) {
        for (VarExprent var : lstVars) {
          mapExprentMinTypes.put(new VarVersionPair(var.getIndex(), 1), var.getVarType());
          mapExprentMaxTypes.put(new VarVersionPair(var.getIndex(), 1), var.getVarType());
        }
      }

      stack.addAll(stat.getStats());
    }
  }

  private static void resetExprentTypes(DirectGraph graph) {
    graph.iterateExprents(new DirectGraph.ExprentIterator() {
      @Override
      public int processExprent(Exprent exprent) {
        List<Exprent> lst = exprent.getAllExprents(true);
        lst.add(exprent);

        for (Exprent expr : lst) {
          if (expr.type == Exprent.EXPRENT_VAR) {
            ((VarExprent)expr).setVarType(VarType.VARTYPE_UNKNOWN);
          }
          else if (expr.type == Exprent.EXPRENT_CONST) {
            ConstExprent constExpr = (ConstExprent)expr;
            if (constExpr.getConstType().typeFamily == CodeConstants.TYPE_FAMILY_INTEGER) {
              constExpr.setConstType(new ConstExprent(constExpr.getIntValue(), constExpr.isBoolPermitted(), null).getConstType());
            }
          }
        }
        return 0;
      }
    });
  }

  private boolean processVarTypes(DirectGraph graph) {
    return graph.iterateExprents(new DirectGraph.ExprentIterator() {
      @Override
      public int processExprent(Exprent exprent) {
        return checkTypeExprent(exprent) ? 0 : 1;
      }
    });
  }

  private boolean checkTypeExprent(Exprent exprent) {

    for (Exprent expr : exprent.getAllExprents()) {
      if (!checkTypeExprent(expr)) {
        return false;
      }
    }

    if (exprent.type == Exprent.EXPRENT_CONST) {
      ConstExprent constExpr = (ConstExprent)exprent;
      if (constExpr.getConstType().typeFamily <= CodeConstants.TYPE_FAMILY_INTEGER) { // boolean or integer
        VarVersionPair pair = new VarVersionPair(constExpr.id, -1);
        if (!mapExprentMinTypes.containsKey(pair)) {
          mapExprentMinTypes.put(pair, constExpr.getConstType());
        }
      }
    }

    CheckTypesResult result = exprent.checkExprTypeBounds();

    for (CheckTypesResult.ExprentTypePair entry : result.getLstMaxTypeExprents()) {
      if (entry.type.typeFamily != CodeConstants.TYPE_FAMILY_OBJECT) {
        changeExprentType(entry.exprent, entry.type, 1);
      }
    }

    boolean res = true;
    for (CheckTypesResult.ExprentTypePair entry : result.getLstMinTypeExprents()) {
      res &= changeExprentType(entry.exprent, entry.type, 0);
    }

    return res;
  }


  private boolean changeExprentType(Exprent exprent, VarType newType, int minMax) {
    boolean res = true;

    switch (exprent.type) {
      case Exprent.EXPRENT_CONST:
        ConstExprent constExpr = (ConstExprent)exprent;
        VarType constType = constExpr.getConstType();

        if (newType.typeFamily > CodeConstants.TYPE_FAMILY_INTEGER || constType.typeFamily > CodeConstants.TYPE_FAMILY_INTEGER) {
          return true;
        }
        else if (newType.typeFamily == CodeConstants.TYPE_FAMILY_INTEGER) {
          VarType minInteger = new ConstExprent((Integer)constExpr.getValue(), false, null).getConstType();
          if (minInteger.isStrictSuperset(newType)) {
            newType = minInteger;
          }
        }
      case Exprent.EXPRENT_VAR:
        VarVersionPair pair = null;
        if (exprent.type == Exprent.EXPRENT_CONST) {
          pair = new VarVersionPair(((ConstExprent)exprent).id, -1);
        }
        else if (exprent.type == Exprent.EXPRENT_VAR) {
          //noinspection ConstantConditions
          pair = new VarVersionPair((VarExprent)exprent);
        }

        if (minMax == 0) { // min
          VarType currentMinType = mapExprentMinTypes.get(pair);
          VarType newMinType;
          if (currentMinType == null || newType.typeFamily > currentMinType.typeFamily) {
            newMinType = newType;
          }
          else if (newType.typeFamily < currentMinType.typeFamily) {
            return true;
          }
          else {
            newMinType = VarType.getCommonSupertype(currentMinType, newType);
          }

          mapExprentMinTypes.put(pair, newMinType);
          if (exprent.type == Exprent.EXPRENT_CONST) {
            //noinspection ConstantConditions
            ((ConstExprent)exprent).setConstType(newMinType);
          }

          if (currentMinType != null && (newMinType.typeFamily > currentMinType.typeFamily || newMinType.isStrictSuperset(currentMinType))) {
            return false;
          }
        }
        else {  // max
          VarType currentMaxType = mapExprentMaxTypes.get(pair);
          VarType newMaxType;
          if (currentMaxType == null || newType.typeFamily < currentMaxType.typeFamily) {
            newMaxType = newType;
          }
          else if (newType.typeFamily > currentMaxType.typeFamily) {
            return true;
          }
          else {
            newMaxType = VarType.getCommonMinType(currentMaxType, newType);
          }

          mapExprentMaxTypes.put(pair, newMaxType);
        }
        break;

      case Exprent.EXPRENT_ASSIGNMENT:
        return changeExprentType(((AssignmentExprent)exprent).getRight(), newType, minMax);

      case Exprent.EXPRENT_FUNCTION:
        FunctionExprent func = (FunctionExprent)exprent;
        switch (func.getFuncType()) {
          case FunctionExprent.FUNCTION_IIF:   // FIXME:
            res = changeExprentType(func.getLstOperands().get(1), newType, minMax) &
                  changeExprentType(func.getLstOperands().get(2), newType, minMax);
            break;
          case FunctionExprent.FUNCTION_AND:
          case FunctionExprent.FUNCTION_OR:
          case FunctionExprent.FUNCTION_XOR:
            res = changeExprentType(func.getLstOperands().get(0), newType, minMax) &
                  changeExprentType(func.getLstOperands().get(1), newType, minMax);
            break;
        }
    }

    return res;
  }

  public Map<VarVersionPair, VarType> getMapExprentMaxTypes() {
    return mapExprentMaxTypes;
  }

  public Map<VarVersionPair, VarType> getMapExprentMinTypes() {
    return mapExprentMinTypes;
  }

  public Map<VarVersionPair, Integer> getMapFinalVars() {
    return mapFinalVars;
  }

  public void setVarType(VarVersionPair pair, VarType type) {
    mapExprentMinTypes.put(pair, type);
  }

  public VarType getVarType(VarVersionPair pair) {
    return mapExprentMinTypes.get(pair);
  }
}