/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ImplicitNumericConversionInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreWideningConversions = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreCharConversions = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreConstantConversions = false;

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("implicit.numeric.conversion.ignore.widening.conversion.option"),
                             "ignoreWideningConversions");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("implicit.numeric.conversion.ignore.char.conversion.option"),
                             "ignoreCharConversions");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("implicit.numeric.conversion.ignore.constant.conversion.option"),
                             "ignoreConstantConversions");
    return optionsPanel;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[1];
    final PsiType expectedType = (PsiType)infos[2];
    return InspectionGadgetsBundle.message(infos[0] instanceof PsiAssignmentExpression
                                           ? "implicit.numeric.conversion.assignment.problem.descriptor"
                                           : "implicit.numeric.conversion.problem.descriptor",
                                           type.getPresentableText(), expectedType.getPresentableText());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ImplicitNumericConversionVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ImplicitNumericConversionFix((PsiExpression)infos[0], (PsiType)infos[2]);
  }

  private class ImplicitNumericConversionFix extends InspectionGadgetsFix {

    private final @IntentionName String m_name;

    ImplicitNumericConversionFix(PsiExpression expression, PsiType expectedType) {
      final String convertedExpression = convertExpression(expression, expectedType);
      if (convertedExpression != null) {
        m_name = CommonQuickFixBundle.message("fix.convert.to.x", convertedExpression);
      }
      else {
        m_name = InspectionGadgetsBundle.message("implicit.numeric.conversion.make.explicit.quickfix");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("implicit.numeric.conversion.make.explicit.quickfix");
    }

    @Override
    @NotNull
    public String getName() {
      return m_name;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
      if (expectedType == null) {
        return;
      }
      final String convertedExpression = convertExpression(expression, expectedType);
      if (convertedExpression != null) {
        PsiReplacementUtil.replaceExpression(expression, convertedExpression);
      }
      else {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
          final PsiJavaToken sign = assignmentExpression.getOperationSign();
          if (!JavaTokenType.EQ.equals(sign.getTokenType())) {
            replaceCompoundAssignment(assignmentExpression);
            return;
          }
        }
        final CommentTracker commentTracker = new CommentTracker();
        final String castExpression =
          '(' + expectedType.getCanonicalText() + ')' + commentTracker.text(expression, ParenthesesUtils.TYPE_CAST_PRECEDENCE);
        PsiReplacementUtil.replaceExpression(expression, castExpression, commentTracker);
      }
    }

    private void replaceCompoundAssignment(PsiAssignmentExpression assignmentExpression) {
      final PsiJavaToken sign = assignmentExpression.getOperationSign();
      if (JavaTokenType.EQ.equals(sign.getTokenType())) throw new IllegalArgumentException();
      final CommentTracker commentTracker = new CommentTracker();
      final PsiExpression lhs = assignmentExpression.getLExpression();
      final String lhsText = commentTracker.text(lhs);
      StringBuilder builder = new StringBuilder();
      builder.append(lhsText).append('=');
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (rhs == null) return;
      final PsiType rhsType = rhs.getType();
      if (rhsType == null) return;
      final PsiType promotedType = TypeConversionUtil.binaryNumericPromotion(assignmentExpression.getType(), rhsType);
      final PsiType lhsType = lhs.getType();
      if (lhsType == null) return;
      final boolean cast = !promotedType.equals(lhsType);
      if (cast) {
        builder.append('(').append(lhsType.getCanonicalText()).append(")(");
        if (!ignoreWideningConversions && (!ignoreCharConversions || !isCharConversion(lhsType, promotedType))) {
          builder.append("(").append(promotedType.getCanonicalText()).append(')');
        }
      }
      builder.append(lhsText).append(StringUtil.substringBefore(sign.getText(), "="));
      if (!ignoreWideningConversions && !promotedType.equals(rhsType) &&
          !(ignoreCharConversions && isCharConversion(rhsType, promotedType))) {
        builder.append('(').append(promotedType.getCanonicalText()).append(')');
      }
      builder.append(commentTracker.text(rhs));
      if (cast) {
        builder.append(')');
      }
      final String newExpressionText = builder.toString();
      PsiReplacementUtil.replaceExpression(assignmentExpression, newExpressionText, commentTracker);
    }

    @Nullable
    @NonNls
    private static String convertExpression(PsiExpression expression, PsiType expectedType) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(expression instanceof PsiLiteralExpression) && !isNegatedLiteral(expression)) {
        return null;
      }
      final PsiType expressionType = expression.getType();
      if (expressionType == null) {
        return null;
      }
      final String text = expression.getText();
      if (expressionType.equals(PsiType.INT) && expectedType.equals(PsiType.LONG)) {
        return text + 'L';
      }
      if (expressionType.equals(PsiType.INT) && expectedType.equals(PsiType.FLOAT)) {
        if (!isDecimalLiteral(text)) {
          return null;
        }
        return text + ".0F";
      }
      if (expressionType.equals(PsiType.INT) && expectedType.equals(PsiType.DOUBLE)) {
        if (!isDecimalLiteral(text)) {
          return null;
        }
        return text + ".0";
      }
      if (expressionType.equals(PsiType.LONG) && expectedType.equals(PsiType.FLOAT)) {
        if (!isDecimalLiteral(text)) {
          return null;
        }
        return text.substring(0, text.length() - 1) + ".0F";
      }
      if (expressionType.equals(PsiType.LONG) && expectedType.equals(PsiType.DOUBLE)) {
        if (!isDecimalLiteral(text)) {
          return null;
        }
        return text.substring(0, text.length() - 1) + ".0";
      }
      if (expressionType.equals(PsiType.DOUBLE) && expectedType.equals(PsiType.FLOAT)) {
        final int length = text.length();
        if (text.charAt(length - 1) == 'd' || text.charAt(length - 1) == 'D') {
          return text.substring(0, length - 1) + 'F';
        }
        else {
          return text + 'F';
        }
      }
      if (expressionType.equals(PsiType.FLOAT) && expectedType.equals(PsiType.DOUBLE)) {
        final int length = text.length();
        return text.substring(0, length - 1);
      }
      return null;
    }

    private static boolean isDecimalLiteral(String text) {
      // should not be binary, octal or hexadecimal: 0b101, 077, 0xFF
      return text.length() > 0 && text.charAt(0) != '0';
    }

    private static boolean isNegatedLiteral(PsiExpression expression) {
      if (!(expression instanceof PsiPrefixExpression)) {
        return false;
      }
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (!JavaTokenType.MINUS.equals(tokenType)) {
        return false;
      }
      final PsiExpression operand = prefixExpression.getOperand();
      return operand instanceof PsiLiteralExpression;
    }
  }

  private static boolean isCharConversion(PsiType expressionType, PsiType convertedType) {
    return PsiType.CHAR.equals(expressionType) && !PsiType.FLOAT.equals(convertedType) && !PsiType.DOUBLE.equals(convertedType) ||
           PsiType.CHAR.equals(convertedType) && !PsiType.FLOAT.equals(expressionType) && !PsiType.DOUBLE.equals(expressionType);
  }

  private class ImplicitNumericConversionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
      super.visitArrayAccessExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiParenthesizedExpression) {
        return;
      }
      if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
        if (assignmentExpression.getOperationTokenType() != JavaTokenType.EQ) {
          if (assignmentExpression.getLExpression() == expression) {
            final PsiExpression rhs = assignmentExpression.getRExpression();
            if (rhs != null) {
              final PsiType expressionType = expression.getType();
              if (!ClassUtils.isPrimitiveNumericType(expressionType)) return;
              final PsiType rhsType = rhs.getType();
              if (!ClassUtils.isPrimitiveNumericType(rhsType)) return;
              final PsiType promotedType = TypeConversionUtil.binaryNumericPromotion(expressionType, rhsType);
              if (checkTypes(assignmentExpression, promotedType, expressionType)) return;
              if (ignoreWideningConversions) return;
              checkTypes(rhs, rhsType, promotedType);
            }
          }
          return;
        }
      }
      if (ignoreWideningConversions) {
        // Further analysis could be quite slow, especially in batch mode, as type of almost every expression is queried.
        // So stop here if ignoreWideningConversions is on.
        return;
      }
      final PsiType expressionType = expression.getType();
      if (!ClassUtils.isPrimitiveNumericType(expressionType)) {
        return;
      }
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
      if (!ClassUtils.isPrimitiveNumericType(expectedType)) {
        return;
      }
      checkTypes(expression, expressionType, expectedType);
    }

    private boolean checkTypes(PsiExpression expression, PsiType expressionType, PsiType convertedType) {
      if (expressionType.equals(convertedType)) return false;
      if (ignoreConstantConversions) {
        PsiExpression rootExpression = expression;
        while (rootExpression instanceof PsiParenthesizedExpression) {
          final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)rootExpression;
          rootExpression = parenthesizedExpression.getExpression();
        }
        if (rootExpression instanceof PsiLiteralExpression || PsiUtil.isConstantExpression(rootExpression)) {
          return false;
        }
      }
      if (ignoreCharConversions && isCharConversion(expressionType, convertedType)) return false;
      registerError(expression instanceof PsiAssignmentExpression
                    ? ((PsiAssignmentExpression)expression).getLExpression()
                    : expression,
                    expression, expressionType, convertedType);
      return true;
    }
  }
}
