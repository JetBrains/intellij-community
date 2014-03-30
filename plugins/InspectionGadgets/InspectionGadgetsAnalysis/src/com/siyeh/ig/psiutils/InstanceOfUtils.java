/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class InstanceOfUtils {

  private InstanceOfUtils() {}

  public static PsiInstanceOfExpression getConflictingInstanceof(PsiType castType, PsiReferenceExpression operand, PsiElement context) {
    if (!(castType instanceof PsiClassType)) {
      return null;
    }
    final PsiClassType classType = (PsiClassType)castType;
    final PsiClassType rawType = classType.rawType();
    final InstanceofChecker checker = new InstanceofChecker(operand, rawType, false);
    PsiElement parent = PsiTreeUtil.getParentOfType(context, PsiIfStatement.class, PsiConditionalExpression.class,
                                                     PsiPolyadicExpression.class);
    while (parent != null) {
      parent.accept(checker);
      if (checker.hasAgreeingInstanceof()) {
        return null;
      }
      parent = PsiTreeUtil.getParentOfType(parent, PsiPolyadicExpression.class, PsiIfStatement.class,
                                           PsiConditionalExpression.class);
    }
    if (checker.hasAgreeingInstanceof()) {
      return null;
    }
    return checker.getConflictingInstanceof();
  }

  public static boolean hasAgreeingInstanceof(
    @NotNull PsiTypeCastExpression expression) {
    final PsiType castType = expression.getType();
    final PsiExpression operand = expression.getOperand();
    if (!(operand instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)operand;
    final InstanceofChecker checker = new InstanceofChecker(
      referenceExpression, castType, false);
    PsiElement parent = PsiTreeUtil.getParentOfType(expression,
                                                    PsiIfStatement.class,
                                                    PsiConditionalExpression.class, PsiPolyadicExpression.class);
    while (parent != null) {
      parent.accept(checker);
      if (checker.hasAgreeingInstanceof()) {
        return true;
      }
      parent = PsiTreeUtil.getParentOfType(parent,
                                           PsiIfStatement.class,
                                           PsiConditionalExpression.class, PsiPolyadicExpression.class);
    }
    return false;
  }

  private static class InstanceofChecker extends JavaElementVisitor {

    private final PsiReferenceExpression referenceExpression;
    private final PsiType castType;
    private final boolean strict;
    private boolean inElse = false;
    private PsiInstanceOfExpression conflictingInstanceof = null;
    private boolean agreeingInstanceof = false;


    InstanceofChecker(PsiReferenceExpression referenceExpression,
                      PsiType castType, boolean strict) {
      this.referenceExpression = referenceExpression;
      this.castType = castType;
      this.strict = strict;
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
      visitExpression(expression);
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType == JavaTokenType.ANDAND) {
        for (PsiExpression operand : expression.getOperands()) {
          checkExpression(operand);
          if (agreeingInstanceof) {
            return;
          }
        }
        if (!inElse && conflictingInstanceof != null) {
          agreeingInstanceof = false;
        }
      }
      else if (tokenType == JavaTokenType.OROR) {
        for (PsiExpression operand : expression.getOperands()) {
          if (operand instanceof PsiPrefixExpression && ((PsiPrefixExpression)operand).getOperationTokenType() == JavaTokenType.EXCL) {
            inElse = true;
          }
          checkExpression(operand);
        }
        if (inElse && conflictingInstanceof != null) {
          agreeingInstanceof = false;
        }
      }
    }

    @Override
    public void visitIfStatement(PsiIfStatement ifStatement) {
      final PsiStatement branch = ifStatement.getElseBranch();
      inElse = branch != null &&
               PsiTreeUtil.isAncestor(branch, referenceExpression, true);
      if (inElse) {
        if (branch instanceof PsiBlockStatement) {
          final PsiBlockStatement blockStatement =
            (PsiBlockStatement)branch;
          if (VariableAccessUtils.variableIsAssignedBeforeReference(
            referenceExpression, blockStatement)) {
            return;
          }
        }
      }
      else {
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        if (thenBranch instanceof PsiBlockStatement) {
          final PsiBlockStatement blockStatement =
            (PsiBlockStatement)thenBranch;
          if (VariableAccessUtils.variableIsAssignedBeforeReference(
            referenceExpression, blockStatement)) {
            return;
          }
        }
      }
      PsiExpression condition = ifStatement.getCondition();
      condition = PsiUtil.deparenthesizeExpression(condition);
      if (condition instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression binaryExpression =
          (PsiPolyadicExpression)condition;
        visitPolyadicExpression(binaryExpression);
      }
      else {
        checkExpression(condition);
      }
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      final PsiExpression elseExpression =
        expression.getElseExpression();
      inElse = elseExpression != null &&
               PsiTreeUtil.isAncestor(elseExpression,
                                      referenceExpression, true);
      PsiExpression condition = expression.getCondition();
      condition = PsiUtil.deparenthesizeExpression(condition);
      if (condition instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression binaryExpression =
          (PsiPolyadicExpression)condition;
        visitPolyadicExpression(binaryExpression);
      }
      else {
        checkExpression(condition);
      }
    }

    private void checkExpression(PsiExpression expression) {
      expression = PsiUtil.deparenthesizeExpression(expression);
      if (inElse) {
        if (expression instanceof PsiPrefixExpression) {
          final PsiPrefixExpression prefixExpression =
            (PsiPrefixExpression)expression;
          final IElementType tokenType =
            prefixExpression.getOperationTokenType();
          if (tokenType != JavaTokenType.EXCL) {
            return;
          }
          expression = PsiUtil.deparenthesizeExpression(
            prefixExpression.getOperand());
          checkInstanceOfExpression(expression);
        }
      }
      else {
        checkInstanceOfExpression(expression);
      }
      if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression binaryExpression =
          (PsiPolyadicExpression)expression;
        visitPolyadicExpression(binaryExpression);
      }
    }

    private void checkInstanceOfExpression(PsiExpression expression) {
      if (!(expression instanceof PsiInstanceOfExpression)) {
        return;
      }
      final PsiInstanceOfExpression instanceOfExpression =
        (PsiInstanceOfExpression)expression;
      if (isAgreeing(instanceOfExpression)) {
        agreeingInstanceof = true;
      }
      else if (isConflicting(instanceOfExpression)) {
        conflictingInstanceof = instanceOfExpression;
      }
    }

    private boolean isConflicting(PsiInstanceOfExpression expression) {
      final PsiExpression conditionOperand = expression.getOperand();
      if (!EquivalenceChecker.expressionsAreEquivalent(
        referenceExpression, conditionOperand)) {
        return false;
      }
      final PsiTypeElement typeElement = expression.getCheckType();
      if (typeElement == null) {
        return false;
      }
      final PsiType type = typeElement.getType();
      if (strict) {
        return !castType.equals(type);
      }
      else {
        return !castType.isAssignableFrom(type);
      }
    }

    private boolean isAgreeing(PsiInstanceOfExpression expression) {
      final PsiExpression conditionOperand = expression.getOperand();
      if (!EquivalenceChecker.expressionsAreEquivalent(
        referenceExpression, conditionOperand)) {
        return false;
      }
      final PsiTypeElement typeElement = expression.getCheckType();
      if (typeElement == null) {
        return false;
      }
      final PsiType type = typeElement.getType();
      if (strict) {
        return castType.equals(type);
      }
      else {
        return castType.isAssignableFrom(type);
      }
    }

    public boolean hasAgreeingInstanceof() {
      return agreeingInstanceof;
    }

    public PsiInstanceOfExpression getConflictingInstanceof() {
      return conflictingInstanceof;
    }
  }
}
