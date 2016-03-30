/*
 * Copyright 2011-2016 Jetbrains s.r.o.
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
import com.siyeh.ig.psiutils.VariableAccessUtils;
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
    final Boolean before = (Boolean)infos[1];
    return InspectionGadgetsBundle.message(
      before.booleanValue() ? "pointless.nullcheck.problem.descriptor" : "pointless.nullcheck.after.problem.descriptor");
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
      final PsiPolyadicExpression polyadicExpression = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class);
      if (polyadicExpression == null) {
        return;
      }
      final StringBuilder replacement = new StringBuilder();
      PsiElement anchor = polyadicExpression.getFirstChild();
      if (!(anchor instanceof PsiExpression)) {
        return;
      }
      PsiExpression expression = (PsiExpression)anchor;
      boolean hasText = false;
      while (expression != null) {
        if (PsiTreeUtil.isAncestor(expression, element, false)) {
          while (anchor != expression) {
            if (hasText && anchor instanceof PsiComment) {
              replacement.append(anchor.getText());
            }
            anchor = anchor.getNextSibling();
          }
          anchor = expression.getNextSibling();
        }
        else {
          while (anchor != expression) {
            if (hasText) {
              replacement.append(anchor.getText());
            }
            anchor = anchor.getNextSibling();
          }
          replacement.append(expression.getText());
          hasText = true;
          anchor = expression.getNextSibling();
        }
        expression = PsiTreeUtil.getNextSiblingOfType(anchor, PsiExpression.class);
      }
      PsiReplacementUtil.replaceExpression(polyadicExpression, replacement.toString());
    }
  }

  private static class PointlessNullCheckVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType operationTokenType = expression.getOperationTokenType();
      if (operationTokenType.equals(JavaTokenType.ANDAND)) {
        final PsiExpression[] operands = expression.getOperands();
        for (int i = 0; i < operands.length - 1; i++) {
          for (int j = i + 1; j < operands.length; j++) {
            if (checkAndedExpressions(operands, i, j)) {
              return;
            }
          }
        }
      }
      else if (operationTokenType.equals(JavaTokenType.OROR)) {
        final PsiExpression[] operands = expression.getOperands();
        for (int i = 0; i < operands.length - 1; i++) {
          for (int j = i + 1; j < operands.length; j++) {
            if (checkOrredExpressions(operands, i, j)) {
              return;
            }
          }
        }
      }
    }

    public boolean checkOrredExpressions(PsiExpression[] operands, int i, int j) {
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(operands[i]);
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(operands[j]);
      final PsiBinaryExpression binaryExpression;
      final PsiPrefixExpression prefixExpression;
      final boolean checkRef;
      if (lhs instanceof PsiBinaryExpression && rhs instanceof PsiPrefixExpression) {
        prefixExpression = (PsiPrefixExpression)rhs;
        binaryExpression = (PsiBinaryExpression)lhs;
        checkRef = true;
      }
      else if (rhs instanceof PsiBinaryExpression && lhs instanceof PsiPrefixExpression) {
        prefixExpression = (PsiPrefixExpression)lhs;
        binaryExpression = (PsiBinaryExpression)rhs;
        checkRef = false;
      }
      else {
        return false;
      }
      final IElementType prefixTokenType = prefixExpression.getOperationTokenType();
      if (!JavaTokenType.EXCL.equals(prefixTokenType)) {
        return false;
      }
      final PsiExpression possibleInstanceofExpression = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQEQ)) {
        return false;
      }
      final PsiVariable variable = checkExpressions(binaryExpression, possibleInstanceofExpression);
      if (variable == null || checkRef && isVariableUsed(operands, i, j, variable)) {
        return false;
      }
      registerError(binaryExpression, binaryExpression, Boolean.valueOf(checkRef));
      return true;
    }

    public boolean checkAndedExpressions(PsiExpression[] operands, int i, int j) {
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(operands[i]);
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(operands[j]);
      final PsiBinaryExpression binaryExpression;
      final PsiExpression possibleInstanceofExpression;
      final boolean checkRef;
      if (lhs instanceof PsiBinaryExpression) {
        binaryExpression = (PsiBinaryExpression)lhs;
        possibleInstanceofExpression = rhs;
        checkRef = true;
      }
      else if (rhs instanceof PsiBinaryExpression) {
        binaryExpression = (PsiBinaryExpression)rhs;
        possibleInstanceofExpression = lhs;
        checkRef = false;
      }
      else {
        return false;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.NE)) {
        return false;
      }
      final PsiVariable variable = checkExpressions(binaryExpression, possibleInstanceofExpression);
      if (variable == null || checkRef && isVariableUsed(operands, i, j, variable)) {
        return false;
      }
      registerError(binaryExpression, binaryExpression, Boolean.valueOf(checkRef));
      return true;
    }

    private static boolean isVariableUsed(PsiExpression[] operands, int i, int j, PsiVariable variable) {
      i++;
      while (i < j) {
        if (VariableAccessUtils.variableIsUsed(variable, operands[i])) {
          return true;
        }
        i++;
      }
      return false;
    }

    public static PsiVariable checkExpressions(PsiBinaryExpression binaryExpression, PsiExpression possibleInstanceofExpression) {
      final PsiReferenceExpression referenceExpression1 = getReferenceFromNullCheck(binaryExpression);
      if (referenceExpression1 == null) {
        return null;
      }
      final PsiElement target1 = referenceExpression1.resolve();
      if (!(target1 instanceof PsiVariable)) {
        return null;
      }
      final PsiVariable variable = (PsiVariable)target1;
      final PsiReferenceExpression referenceExpression2 = getReferenceFromInstanceofExpression(possibleInstanceofExpression);
      if (referenceExpression2 == null || !referenceExpression2.isReferenceTo(variable)) {
        return null;
      }
      return variable;
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
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return null;
        }
        final PsiVariable variable = (PsiVariable)target;
        for (int i = 1, operandsLength = operands.length; i < operandsLength; i++) {
          final PsiReferenceExpression reference2 = getReferenceFromInstanceofExpression(operands[i]);
          if (reference2 == null || !reference2.isReferenceTo(variable)) {
            return null;
          }
        }
        return referenceExpression;
      } else {
        return null;
      }
    }
  }
}
