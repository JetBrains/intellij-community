// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.assignment;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public abstract class BaseAssignmentToParameterInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreTransformationOfOriginalParameter = false;

  protected abstract boolean isApplicable(PsiParameter parameter);

  @Override
  public final BaseInspectionVisitor buildVisitor() {
    return new AssignmentToParameterVisitor();
  }

  private class AssignmentToParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression lhs = expression.getLExpression();
      final PsiParameter parameter = getParameter(lhs);
      if (parameter == null) {
        return;
      }
      if (ignoreTransformationOfOriginalParameter) {
        final PsiExpression rhs = expression.getRExpression();
        if (rhs != null && VariableAccessUtils.variableIsUsed(parameter, rhs)) {
          return;
        }
        final IElementType tokenType = expression.getOperationTokenType();
        if (tokenType == JavaTokenType.PLUSEQ ||
            tokenType == JavaTokenType.MINUSEQ ||
            tokenType == JavaTokenType.ASTERISKEQ ||
            tokenType == JavaTokenType.DIVEQ ||
            tokenType == JavaTokenType.ANDEQ ||
            tokenType == JavaTokenType.OREQ ||
            tokenType == JavaTokenType.XOREQ ||
            tokenType == JavaTokenType.PERCEQ ||
            tokenType == JavaTokenType.LTLTEQ ||
            tokenType == JavaTokenType.GTGTEQ ||
            tokenType == JavaTokenType.GTGTGTEQ) {
          return;
        }
      }
      registerError(lhs);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      if (ignoreTransformationOfOriginalParameter) {
        return;
      }
      super.visitUnaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiParameter parameter = getParameter(operand);
      if (parameter == null) {
        return;
      }
      registerError(operand);
    }

    @Nullable
    private PsiParameter getParameter(PsiExpression expression) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (!(expression instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement variable = referenceExpression.resolve();
      if (!(variable instanceof PsiParameter)) {
        return null;
      }
      final PsiParameter parameter = (PsiParameter)variable;
      return !isApplicable(parameter) ? null : parameter;
    }
  }
}
