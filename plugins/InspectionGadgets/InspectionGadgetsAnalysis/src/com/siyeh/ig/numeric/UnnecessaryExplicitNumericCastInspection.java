// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class UnnecessaryExplicitNumericCastInspection extends BaseInspection {
  private static final Set<IElementType> binaryPromotionOperators = Set.of(
    JavaTokenType.ASTERISK,
    JavaTokenType.DIV,
    JavaTokenType.PERC,
    JavaTokenType.PLUS,
    JavaTokenType.MINUS,
    JavaTokenType.LT,
    JavaTokenType.LE,
    JavaTokenType.GT,
    JavaTokenType.GE,
    JavaTokenType.EQEQ,
    JavaTokenType.NE,
    JavaTokenType.AND,
    JavaTokenType.XOR,
    JavaTokenType.OR
  );

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    return InspectionGadgetsBundle.message("unnecessary.explicit.numeric.cast.problem.descriptor", expression.getText());
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryExplicitNumericCastFix();
  }

  private static class UnnecessaryExplicitNumericCastFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.explicit.numeric.cast.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)parent;
      if (!isUnnecessaryPrimitiveNumericCast(typeCastExpression)) {
        return;
      }
      PsiElement grandParent = parent.getParent();
      while (grandParent instanceof PsiParenthesizedExpression) {
        parent = grandParent;
        grandParent = parent.getParent();
      }
      final PsiExpression operand = typeCastExpression.getOperand();
      if (operand == null) {
        parent.delete();
      }
      else {
        parent.replace(operand);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryExplicitNumericCastVisitor();
  }

  private static class UnnecessaryExplicitNumericCastVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      if (!isUnnecessaryPrimitiveNumericCast(expression)) {
        // equal types is caught by "Redundant type cast" inspection
        return;
      }
      final PsiTypeElement typeElement = expression.getCastType();
      if (typeElement != null) {
        registerError(typeElement, ProblemHighlightType.LIKE_UNUSED_SYMBOL, expression.getOperand());
      }
    }
  }

  public static boolean isUnnecessaryPrimitiveNumericCast(PsiTypeCastExpression expression) {
    final PsiType castType = expression.getType();
    if (!ClassUtils.isPrimitiveNumericType(castType)) {
      return false;
    }
    final PsiExpression operand = expression.getOperand();
    if (operand == null) {
      return false;
    }
    final PsiType operandType = operand.getType();
    if (!ClassUtils.isPrimitiveNumericType(operandType)) {
      return false;
    }
    PsiElement parent = expression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiPrefixExpression) {
      // JLS 5.6 Numeric Contexts
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (JavaTokenType.MINUS == tokenType || JavaTokenType.PLUS == tokenType || JavaTokenType.TILDE == tokenType) {
        if (TypeUtils.isNarrowingConversion(operandType, castType)) {
          return false;
        }
        if (PsiType.INT.equals(castType)) {
          return !PsiType.LONG.equals(operandType) && !PsiType.FLOAT.equals(operandType) && !PsiType.DOUBLE.equals(operandType);
        }
      }
      return false;
    }
    if (castType.equals(operandType)) {
      // cast to the same type is caught by "Redundant type cast" inspection
      return false;
    }
    if (parent instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (binaryPromotionOperators.contains(tokenType)) {
        if (TypeUtils.isNarrowingConversion(operandType, castType)) {
          return false;
        }
        if (PsiType.INT.equals(castType)) {
          if (PsiType.CHAR.equals(operandType) && TypeUtils.getStringType(polyadicExpression).equals(polyadicExpression.getType())) {
            return false;
          }
          return !PsiType.LONG.equals(operandType) && !PsiType.FLOAT.equals(operandType) && !PsiType.DOUBLE.equals(operandType);
        }
        if (PsiType.LONG.equals(castType) || PsiType.FLOAT.equals(castType) || PsiType.DOUBLE.equals(castType)) {
          final PsiExpression[] operands = polyadicExpression.getOperands();
          int expressionIndex = -1;
          for (int i = 0; i < operands.length; i++) {
            if (expressionIndex == 0 && i > 1) {
              return false;
            }
            final PsiExpression operand1 = operands[i];
            if (PsiTreeUtil.isAncestor(operand1, expression, false)) {
              if (i > 0) {
                return false;
              }
              else {
                expressionIndex = i;
                continue;
              }
            }
            final PsiType type = operand1.getType();
            if (castType.equals(type)) {
              return true;
            }
          }
        }
      }
      else if (ExpressionParser.SHIFT_OPS.contains(tokenType)) {
        final PsiExpression firstOperand = polyadicExpression.getOperands()[0];
        if (!PsiTreeUtil.isAncestor(firstOperand, expression, false)) {
          return true;
        }
        return !PsiType.LONG.equals(castType) && isLegalWideningConversion(operand, PsiType.INT);
      }
      return false;
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      final PsiType lhsType = assignmentExpression.getType();
      if (castType.equals(lhsType) && (isLegalAssignmentConversion(operand, lhsType) || isLegalWideningConversion(operand, lhsType))) return true;
    }
    else if (parent instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)parent;
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null || typeElement.isInferredType()) {
        return false;
      }
      final PsiType lhsType = variable.getType();
      if (castType.equals(lhsType) && (isLegalAssignmentConversion(operand, lhsType) || isLegalWideningConversion(operand, lhsType))) return true;
    }
    else if (MethodCallUtils.isNecessaryForSurroundingMethodCall(expression, operand)) {
      return false;
    }
    final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
    return operandType.equals(expectedType) && isLegalAssignmentConversion(operand, castType) ||
           castType.equals(expectedType) && isLegalWideningConversion(operand, castType);
  }

  static boolean isLegalWideningConversion(PsiExpression expression, PsiType requiredType) {
    final PsiType operandType = expression.getType();
    if (PsiType.DOUBLE.equals(requiredType)) {
      return PsiType.FLOAT.equals(operandType) ||
             PsiType.LONG.equals(operandType) ||
             PsiType.INT.equals(operandType) ||
             PsiType.CHAR.equals(operandType) ||
             PsiType.SHORT.equals(operandType) ||
             PsiType.BYTE.equals(operandType);
    }
    else if (PsiType.FLOAT.equals(requiredType)) {
      return PsiType.LONG.equals(operandType) ||
             PsiType.INT.equals(operandType) ||
             PsiType.CHAR.equals(operandType) ||
             PsiType.SHORT.equals(operandType) ||
             PsiType.BYTE.equals(operandType);
    }
    else if (PsiType.LONG.equals(requiredType)) {
      return PsiType.INT.equals(operandType) ||
             PsiType.CHAR.equals(operandType) ||
             PsiType.SHORT.equals(operandType) ||
             PsiType.BYTE.equals(operandType);
    }
    else if (PsiType.INT.equals(requiredType)) {
      return PsiType.CHAR.equals(operandType) ||
             PsiType.SHORT.equals(operandType) ||
             PsiType.BYTE.equals(operandType);
    }
    return false;
  }

  static boolean isLegalAssignmentConversion(PsiExpression expression, PsiType assignmentType) {
    // JLS 5.2 Assignment Conversion
    if (PsiType.SHORT.equals(assignmentType)) {
      return canValueBeContained(expression, Short.MIN_VALUE, Short.MAX_VALUE);
    }
    else if (PsiType.CHAR.equals(assignmentType)) {
      return canValueBeContained(expression, Character.MIN_VALUE, Character.MAX_VALUE);
    }
    else if (PsiType.BYTE.equals(assignmentType)) {
      return canValueBeContained(expression, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }
    return false;
  }

  private static boolean canValueBeContained(PsiExpression expression, int lowerBound, int upperBound) {
    final PsiType expressionType = expression.getType();
    if (!PsiType.INT.equals(expressionType)) {
      return false;
    }
    final Object constant = ExpressionUtils.computeConstantExpression(expression);
    if (!(constant instanceof Integer)) {
      return false;
    }
    final int i = ((Integer)constant).intValue();
    return i >= lowerBound && i <= upperBound;
  }
}
