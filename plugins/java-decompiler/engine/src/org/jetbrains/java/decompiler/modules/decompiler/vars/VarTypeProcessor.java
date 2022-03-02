// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class VarTypeProcessor {
  private final StructMethod method;
  private final MethodDescriptor methodDescriptor;

  private final Map<VarVersionPair, VarType> minExprentTypes = new HashMap<>();
  private final Map<VarVersionPair, VarType> maxExprentTypes = new HashMap<>();
  private final Map<VarVersionPair, Integer> finalVariables = new HashMap<>();

  public VarTypeProcessor(@NotNull StructMethod method, @NotNull MethodDescriptor methodDescriptor) {
    this.method = method;
    this.methodDescriptor = methodDescriptor;
  }

  public void calculateVarTypes(@NotNull RootStatement root, @NotNull DirectGraph graph) {
    setInitVariables();
    setCatchBlockVariables(root);
    resetExprentTypes(graph);
    //noinspection StatementWithEmptyBody
    while (!processVarTypes(graph)) ;
  }

  private void setInitVariables() {
    boolean thisVar = !method.hasModifier(CodeConstants.ACC_STATIC);
    if (thisVar) {
      StructClass currentClass = (StructClass)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS);
      VarType classType = new VarType(CodeConstants.TYPE_OBJECT, 0, currentClass.qualifiedName);
      minExprentTypes.put(new VarVersionPair(0, 1), classType);
      maxExprentTypes.put(new VarVersionPair(0, 1), classType);
    }
    int varIndex = 0;
    VarType[] methodParameters = methodDescriptor.params;
    for (VarType parameter : methodParameters) {
      minExprentTypes.put(new VarVersionPair(varIndex + (thisVar ? 1 : 0), 1), parameter);
      maxExprentTypes.put(new VarVersionPair(varIndex + (thisVar ? 1 : 0), 1), parameter);
      varIndex += parameter.getStackSize();
    }
  }

  private void setCatchBlockVariables(@NotNull RootStatement root) {
    LinkedList<Statement> statements = new LinkedList<>();
    statements.add(root);
    while (!statements.isEmpty()) {
      Statement statement = statements.removeFirst();
      List<VarExprent> catchVariables = null;
      if (statement.type == StatementType.CATCH_ALL) {
        catchVariables = ((CatchAllStatement)statement).getVars();
      }
      else if (statement.type == StatementType.TRY_CATCH) {
        catchVariables = ((CatchStatement)statement).getVars();
      }
      if (catchVariables != null) {
        for (VarExprent catchVariable : catchVariables) {
          minExprentTypes.put(new VarVersionPair(catchVariable.getIndex(), 1), catchVariable.getVarType());
          maxExprentTypes.put(new VarVersionPair(catchVariable.getIndex(), 1), catchVariable.getVarType());
        }
      }
      statements.addAll(statement.getStats());
    }
  }

  private boolean checkTypeExprent(@NotNull Exprent currentExprent) {
    for (Exprent exprent : currentExprent.getAllExprents()) {
      if (!checkTypeExprent(exprent)) return false;
    }
    if (currentExprent.type == Exprent.EXPRENT_CONST) {
      ConstExprent constExprent = (ConstExprent)currentExprent;
      if (constExprent.getConstType().getTypeFamily() <= CodeConstants.TYPE_FAMILY_INTEGER) { // boolean or integer
        VarVersionPair varVersion = new VarVersionPair(constExprent.id, -1);
        if (!minExprentTypes.containsKey(varVersion)) {
          minExprentTypes.put(varVersion, constExprent.getConstType());
        }
      }
    }

    CheckTypesResult exprentTypeBounds = currentExprent.checkExprTypeBounds();
    if (exprentTypeBounds == null) return true;

    for (var entry : exprentTypeBounds.getMaxTypeExprents()) {
      if (entry.type.getTypeFamily() != CodeConstants.TYPE_FAMILY_OBJECT) {
        changeExprentType(entry.exprent, entry.type, false);
      }
    }
    boolean result = true;
    for (var entry : exprentTypeBounds.getMinTypeExprents()) {
      result &= changeExprentType(entry.exprent, entry.type, true);
    }
    return result;
  }

  private boolean processVarTypes(@NotNull DirectGraph graph) {
    return graph.iterateExprents(exprent -> checkTypeExprent(exprent) ? 0 : 1);
  }

  private boolean changeExprentType(@NotNull Exprent exprent, @NotNull VarType newType, boolean checkMinExprentType) {
    if (exprent.type == Exprent.EXPRENT_CONST) {
      ConstExprent constExprent = (ConstExprent)exprent;
      VarType constType = constExprent.getConstType();
      if (newType.getTypeFamily() > CodeConstants.TYPE_FAMILY_INTEGER || constType.getTypeFamily() > CodeConstants.TYPE_FAMILY_INTEGER) return true;
      if (newType.getTypeFamily() == CodeConstants.TYPE_FAMILY_INTEGER) {
        VarType integerType = new ConstExprent((Integer)constExprent.getValue(), false, null).getConstType();
        if (integerType.isStrictSuperset(newType)) {
          newType = integerType;
        }
      }
      return changeConstExprentType(new VarVersionPair(exprent.id, -1), exprent, newType, checkMinExprentType);
    }
    if (exprent.type == Exprent.EXPRENT_VAR) {
      return changeConstExprentType(new VarVersionPair((VarExprent)exprent), exprent, newType, checkMinExprentType);
    }
    if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
      return changeExprentType(((AssignmentExprent)exprent).getRight(), newType, checkMinExprentType);
    }
    if (exprent.type == Exprent.EXPRENT_FUNCTION) {
      FunctionExprent functionExprent = (FunctionExprent)exprent;
      switch (functionExprent.getFuncType()) {
        case FunctionExprent.FUNCTION_IIF:   // FIXME:
          return changeExprentType(functionExprent.getLstOperands().get(1), newType, checkMinExprentType) &
                 changeExprentType(functionExprent.getLstOperands().get(2), newType, checkMinExprentType);
        case FunctionExprent.FUNCTION_AND:
        case FunctionExprent.FUNCTION_OR:
        case FunctionExprent.FUNCTION_XOR:
          return changeExprentType(functionExprent.getLstOperands().get(0), newType, checkMinExprentType) &
                 changeExprentType(functionExprent.getLstOperands().get(1), newType, checkMinExprentType);
      }
    }
    return true;
  }

  private boolean changeConstExprentType(@NotNull VarVersionPair varVersion,
                                         @NotNull Exprent exprent,
                                         @NotNull VarType newType,
                                         boolean checkMinExprentType) {
    if (checkMinExprentType) {
      VarType currentMinType = minExprentTypes.get(varVersion);
      VarType newMinType;
      if (currentMinType == null || newType.getTypeFamily() > currentMinType.getTypeFamily()) {
        newMinType = newType;
      }
      else if (newType.getTypeFamily() < currentMinType.getTypeFamily()) {
        return true;
      }
      else {
        newMinType = VarType.getCommonSupertype(currentMinType, newType);
      }
      minExprentTypes.put(varVersion, newMinType);
      if (exprent.type == Exprent.EXPRENT_CONST) {
        ((ConstExprent)exprent).setConstType(newMinType);
      }
      return currentMinType == null ||
             (newMinType.getTypeFamily() <= currentMinType.getTypeFamily() && !newMinType.isStrictSuperset(currentMinType));
    }
    VarType currentMaxType = maxExprentTypes.get(varVersion);
    VarType newMaxType;
    if (currentMaxType == null || newType.getTypeFamily() < currentMaxType.getTypeFamily()) {
      newMaxType = newType;
    }
    else if (newType.getTypeFamily() > currentMaxType.getTypeFamily()) {
      return true;
    }
    else {
      newMaxType = VarType.getCommonMinType(currentMaxType, newType);
    }
    maxExprentTypes.put(varVersion, newMaxType);
    return true;
  }

  private static void resetExprentTypes(@NotNull DirectGraph graph) {
    graph.iterateExprents(currExprent -> {
      List<Exprent> allExprents = currExprent.getAllExprents(true);
      allExprents.add(currExprent);
      for (Exprent exprent : allExprents) {
        if (exprent.type == Exprent.EXPRENT_VAR) {
          ((VarExprent)exprent).setVarType(VarType.VARTYPE_UNKNOWN);
        }
        else if (exprent.type == Exprent.EXPRENT_CONST) {
          ConstExprent constExprent = (ConstExprent)exprent;
          if (constExprent.getConstType().getTypeFamily() == CodeConstants.TYPE_FAMILY_INTEGER) {
            constExprent.setConstType(new ConstExprent(constExprent.getIntValue(), constExprent.isBoolPermitted(), null).getConstType());
          }
        }
      }
      return 0;
    });
  }

  public Map<VarVersionPair, VarType> getMaxExprentTypes() {
    return maxExprentTypes;
  }

  public Map<VarVersionPair, VarType> getMinExprentTypes() {
    return minExprentTypes;
  }

  public Map<VarVersionPair, Integer> getFinalVariables() {
    return finalVariables;
  }

  public VarType getVarType(VarVersionPair pair) {
    return minExprentTypes.get(pair);
  }

  public void setVarType(VarVersionPair pair, VarType type) {
    minExprentTypes.put(pair, type);
  }
}