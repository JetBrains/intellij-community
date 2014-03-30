/*
 * Copyright 2011-2013 Jetbrains s.r.o.
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This inspection finds instances of null checks followed by an instanceof check
 * on the same variable. For instance:
 * <code>
 * if (x != null && x instanceof String) { ... }
 * </code>
 * The instanceof operator returns false when passed a null, so the null check is pointless.
 *
 * @author Lars Fischer
 * @author Etienne Studer
 * @author Hamlet D'Arcy
 */
public class PointlessNullCheckInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("pointless.nullcheck.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("pointless.nullcheck.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessNullCheckVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    return new PointlessNullCheckFix(expression.getText());
  }

  private static class PointlessNullCheckFix extends InspectionGadgetsFix {

    private final String myExpressionText;

    public PointlessNullCheckFix(String expressionText) {
      myExpressionText = expressionText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("pointless.nullcheck.simplify.quickfix", myExpressionText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PsiBinaryExpression.class);
      if (binaryExpression == null) {
        return;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(rhs, element, false)) {
        PsiReplacementUtil.replaceExpression(binaryExpression, lhs.getText());
      }
      else if (PsiTreeUtil.isAncestor(lhs, element, false)) {
        PsiReplacementUtil.replaceExpression(binaryExpression, rhs.getText());
      }
    }
  }

  private static class PointlessNullCheckVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType operationTokenType = expression.getOperationTokenType();
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(expression.getLOperand());
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getROperand());
      final PsiBinaryExpression binaryExpression;
      final PsiExpression possibleInstanceofExpression;
      if (operationTokenType.equals(JavaTokenType.ANDAND)) {
        if (lhs instanceof PsiBinaryExpression) {
          binaryExpression = (PsiBinaryExpression)lhs;
          possibleInstanceofExpression = rhs;
        }
        else if (rhs instanceof PsiBinaryExpression) {
          binaryExpression = (PsiBinaryExpression)rhs;
          possibleInstanceofExpression = lhs;
        }
        else {
          return;
        }
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.NE)) {
          return;
        }
      }
      else if (operationTokenType.equals(JavaTokenType.OROR)) {
        if (lhs instanceof PsiBinaryExpression && rhs instanceof PsiPrefixExpression) {
          final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)rhs;
          final IElementType prefixTokenType = prefixExpression.getOperationTokenType();
          if (!JavaTokenType.EXCL.equals(prefixTokenType)) {
            return;
          }
          binaryExpression = (PsiBinaryExpression)lhs;
          possibleInstanceofExpression = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
        }
        else if (rhs instanceof PsiBinaryExpression && lhs instanceof PsiPrefixExpression) {
          final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)lhs;
          final IElementType prefixTokenType = prefixExpression.getOperationTokenType();
          if (!JavaTokenType.EXCL.equals(prefixTokenType)) {
            return;
          }
          binaryExpression = (PsiBinaryExpression)rhs;
          possibleInstanceofExpression = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
        }
        else {
          return;
        }
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.EQEQ)) {
          return;
        }
      }
      else {
        return;
      }
      final PsiReferenceExpression referenceExpression1 = getReferenceFromNullCheck(binaryExpression);
      if (referenceExpression1 == null) {
        return;
      }
      final PsiReferenceExpression referenceExpression2 = getReferenceFromInstanceofExpression(possibleInstanceofExpression);
      if (!referencesEqual(referenceExpression1, referenceExpression2)) {
        return;
      }
      registerError(binaryExpression, binaryExpression);
    }

    @Nullable
    private static PsiReferenceExpression getReferenceFromNullCheck(PsiBinaryExpression expression) {
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(expression.getLOperand());
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getROperand());
      if (lhs instanceof PsiReferenceExpression) {
        if (!(rhs instanceof PsiLiteralExpression && PsiType.NULL.equals(rhs.getType()))) {
          return null;
        }
        return (PsiReferenceExpression)lhs;
      }
      else if (rhs instanceof PsiReferenceExpression) {
        if (!(lhs instanceof PsiLiteralExpression && PsiType.NULL.equals(lhs.getType()))) {
          return null;
        }
        return (PsiReferenceExpression)rhs;
      }
      else {
        return null;
      }
    }

    @Nullable
    private static PsiReferenceExpression getReferenceFromInstanceofExpression(PsiExpression expression) {
      if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        return getReferenceFromInstanceofExpression(parenthesizedExpression.getExpression());
      }
      else if (expression instanceof PsiInstanceOfExpression) {
        final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
        final PsiExpression operand = ParenthesesUtils.stripParentheses(instanceOfExpression.getOperand());
        if (!(operand instanceof PsiReferenceExpression)) {
          return null;
        }
        return (PsiReferenceExpression)operand;
      }
      else if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (JavaTokenType.OROR != tokenType) {
          return null;
        }
        final PsiExpression[] operands = polyadicExpression.getOperands();
        final PsiReferenceExpression referenceExpression = getReferenceFromInstanceofExpression(operands[0]);
        if (referenceExpression == null) {
          return null;
        }
        for (int i = 1, operandsLength = operands.length; i < operandsLength; i++) {
          if (!referencesEqual(referenceExpression, getReferenceFromInstanceofExpression(operands[i]))) {
            return null;
          }
        }
        return referenceExpression;
      } else {
        return null;
      }
    }
  }

  private static boolean referencesEqual(PsiReferenceExpression reference1, PsiReferenceExpression reference2) {
    if (reference1 ==  null || reference2 == null) {
      return false;
    }
    final PsiElement target1 = reference1.resolve();
    if (target1 == null) {
      return false;
    }
    final PsiElement target2 = reference2.resolve();
    return target1.equals(target2);
  }
}
