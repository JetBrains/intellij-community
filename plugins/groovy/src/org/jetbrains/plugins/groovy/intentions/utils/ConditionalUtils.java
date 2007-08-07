package org.jetbrains.plugins.groovy.intentions.utils;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrBlockStatement;

public class ConditionalUtils {

  private ConditionalUtils() {
    super();
  }

  public static GrStatement stripBraces(GrStatement branch) {
    if (branch instanceof GrBlockStatement) {
      final GrBlockStatement block = (GrBlockStatement) branch;
      final GrOpenBlock codeBlock = block.getBlock();
      final GrStatement[] statements = codeBlock.getStatements();
      if (statements.length == 1) {
        return statements[0];
      } else {
        return block;
      }
    } else {
      return branch;
    }
  }

  public static boolean isReturn(GrStatement statement, @NonNls String value) {
    if (statement == null) {
      return false;
    }
    if (!(statement instanceof GrReturnStatement)) {
      return false;
    }
    final GrReturnStatement returnStatement =
        (GrReturnStatement) statement;
    final GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      return false;
    }
    final String returnValueText = returnValue.getText();
    return value.equals(returnValueText);
  }

  public static boolean isAssignment(GrStatement statement, @NonNls String value) {
    if (statement == null) {
      return false;
    }
    if (!(statement instanceof GrExpression)) {
      return false;
    }
    final GrExpression expression = (GrExpression) statement;
    if (!(expression instanceof GrAssignmentExpression)) {
      return false;
    }
    final GrAssignmentExpression assignment =
        (GrAssignmentExpression) expression;
    final GrExpression rhs = assignment.getRValue();
    if (rhs == null) {
      return false;
    }
    final String rhsText = rhs.getText();
    return value.equals(rhsText);
  }

  public static boolean isAssignment(GrStatement statement) {
    return statement instanceof GrAssignmentExpression;
  }
}
