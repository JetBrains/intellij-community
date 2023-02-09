// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

class ExpandBooleanPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof GrStatement statement)) {
      return false;
    }
    return isBooleanReturn(statement) || isBooleanAssignment(statement);
  }

  public static boolean isBooleanReturn(GrStatement statement) {
    if (!(statement instanceof GrReturnStatement returnStatement)) {
      return false;
    }
    final GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      return false;
    }
    if (returnValue instanceof GrLiteral) {
      return false;
    }
    final PsiType returnType = returnValue.getType();
    if (returnType == null) {
      return false;
    }
    return returnType.equals(PsiTypes.booleanType()) || returnType.equalsToText("java.lang.Boolean");
  }

  public static boolean isBooleanAssignment(GrStatement expression) {

    if (!(expression instanceof GrAssignmentExpression assignment)) {
      return false;
    }
    final GrExpression rhs = assignment.getRValue();
    if (rhs == null) {
      return false;
    }
    if (rhs instanceof GrLiteral) {
      return false;
    }
    final PsiType assignmentType = rhs.getType();
    if (assignmentType == null) {
      return false;
    }
    return assignmentType.equals(PsiTypes.booleanType()) || assignmentType.equalsToText("java.lang.Boolean");
  }
}
