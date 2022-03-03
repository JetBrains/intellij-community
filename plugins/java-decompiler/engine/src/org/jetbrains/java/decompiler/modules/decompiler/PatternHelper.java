// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.struct.StructClass;

import java.util.List;

public class PatternHelper {

  /**
   * Method searches if-pattern like <code>if (var instanceof SomeType)</code> pattern,
   * and assignment expression pattern like <code>SomeType s = (SomeType)var;</code>.
   * If the pattern were found, then method replaces found assignment expression with pattern variable.
   *
   * @param statement root statement to start traversal
   * @param structClass owner class of <code>statement</code>
   */
  public static void replaceAssignmentsWithPatternVariables(RootStatement statement, StructClass structClass) {
    if (!structClass.isVersion16()) return;
    replaceAssignmentsWithPatternVariables(statement);
  }

  private static void replaceAssignmentsWithPatternVariables(Statement statement) {
    if (statement instanceof IfStatement) {
      IfStatement ifStatement = (IfStatement)statement;
      FunctionExprent instanceOfExprent = findInstanceofExprent(ifStatement);
      if (instanceOfExprent == null) return;

      List<Exprent> operands = instanceOfExprent.getLstOperands();
      if (operands.size() != 2 || operands.get(0).type != Exprent.EXPRENT_VAR || operands.get(1).type != Exprent.EXPRENT_CONST) return;
      VarExprent operand = (VarExprent)operands.get(0);
      ConstExprent checkType = (ConstExprent)operands.get(1);

      PatternVariableCandidate patternVarCandidate = findPatternVarCandidate(ifStatement.getIfstat(), operand, checkType);
      if (patternVarCandidate == null) {
        patternVarCandidate = findPatternVarCandidate(ifStatement.getElsestat(), operand, checkType);
      }
      if (patternVarCandidate == null) return;

      instanceOfExprent.getLstOperands().remove(1);
      instanceOfExprent.getLstOperands().add(patternVarCandidate.varExprent);
      patternVarCandidate.ifElseStat.getExprents().remove(patternVarCandidate.assignmentExprent);
      return;
    }
    for (Statement child : statement.getStats()) {
      replaceAssignmentsWithPatternVariables(child);
    }
  }

  private static FunctionExprent findInstanceofExprent(IfStatement ifStat) {
    return ifStat.getHeadexprent().getAllExprents(true).stream()
      .filter(expr -> expr.type == Exprent.EXPRENT_FUNCTION).map(expr -> (FunctionExprent)expr)
      .filter(expr -> expr.getFuncType() == FunctionExprent.FUNCTION_INSTANCEOF)
      .findFirst().orElse(null);
  }

  private static PatternVariableCandidate findPatternVarCandidate(Statement ifElseStat, VarExprent operand, ConstExprent checkType) {
    if (ifElseStat instanceof BasicBlockStatement) {
      List<Exprent> ifElseExprents = ifElseStat.getExprents();
      if (ifElseExprents.isEmpty() || ifElseExprents.get(0).type != Exprent.EXPRENT_ASSIGNMENT) return null;

      AssignmentExprent assignmentExprent = (AssignmentExprent)ifElseExprents.get(0);
      if (assignmentExprent.getLeft().type != Exprent.EXPRENT_VAR) return null;
      VarExprent varExprent = (VarExprent)assignmentExprent.getLeft();
      if (!varExprent.isDefinition()) return null;
      if (assignmentExprent.getRight().type != Exprent.EXPRENT_FUNCTION) return null;
      FunctionExprent castExprent = (FunctionExprent)assignmentExprent.getRight();
      if (castExprent.getFuncType() != FunctionExprent.FUNCTION_CAST) return null;

      List<Exprent> castExprents = castExprent.getAllExprents();
      if (castExprents.size() == 2 && operand.equals(castExprents.get(0)) && checkType.equals(castExprents.get(1))) {
        return new PatternVariableCandidate(ifElseStat, assignmentExprent, varExprent);
      }
      return null;
    }
    else if (ifElseStat instanceof IfStatement || ifElseStat instanceof SequenceStatement) {
      return findPatternVarCandidate(ifElseStat.getFirst(), operand, checkType);
    }
    return null;
  }

  private static class PatternVariableCandidate {
    private final Statement ifElseStat;
    private final AssignmentExprent assignmentExprent;
    private final VarExprent varExprent;

    private PatternVariableCandidate(Statement ifElseStat, AssignmentExprent assignmentExprent, VarExprent varExprent) {
      this.ifElseStat = ifElseStat;
      this.assignmentExprent = assignmentExprent;
      this.varExprent = varExprent;
    }
  }
}
