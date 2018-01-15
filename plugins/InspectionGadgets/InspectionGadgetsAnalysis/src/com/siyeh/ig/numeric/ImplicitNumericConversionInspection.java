/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
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
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("implicit.numeric.conversion.display.name");
  }

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
    return InspectionGadgetsBundle.message("implicit.numeric.conversion.problem.descriptor",
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

  private static class ImplicitNumericConversionFix extends InspectionGadgetsFix {

    private final String m_name;

    ImplicitNumericConversionFix(PsiExpression expression, PsiType expectedType) {
      final String convertedExpression = convertExpression(expression, expectedType);
      if (convertedExpression != null) {
        m_name = InspectionGadgetsBundle.message("implicit.numeric.conversion.convert.quickfix", convertedExpression);
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
            CommentTracker commentTracker = new CommentTracker();
            final String lhsText = commentTracker.text(assignmentExpression.getLExpression());
            final String newExpressionText =
              lhsText + "=(" + expectedType.getCanonicalText() + ")(" + lhsText + sign.getText().charAt(0) + commentTracker.text(expression) + ')';
            PsiReplacementUtil.replaceExpression(assignmentExpression, newExpressionText, commentTracker);
            return;
          }
        }
        CommentTracker commentTracker = new CommentTracker();
        final String castExpression;
        if (ParenthesesUtils.getPrecedence(expression) <= ParenthesesUtils.TYPE_CAST_PRECEDENCE) {
          castExpression = '(' + expectedType.getCanonicalText() + ')' + commentTracker.text(expression);
        }
        else {
          castExpression = '(' + expectedType.getCanonicalText() + ")(" + commentTracker.text(expression) + ')';
        }
        PsiReplacementUtil.replaceExpression(expression, castExpression, commentTracker);
      }
    }

    @Nullable
    @NonNls
    private static String convertExpression(PsiExpression expression, PsiType expectedType) {
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

    private static boolean isIntegral(@Nullable PsiType expressionType) {
      return PsiType.INT.equals(expressionType) || PsiType.LONG.equals(expressionType);
    }

    private static boolean isFloatingPoint(@Nullable PsiType expressionType) {
      return PsiType.FLOAT.equals(expressionType) || PsiType.DOUBLE.equals(expressionType);
    }
  }

  private class ImplicitNumericConversionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitUnaryExpression(PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiParenthesizedExpression) {
        return;
      }
      if (ignoreConstantConversions) {
        PsiExpression rootExpression = expression;
        while (rootExpression instanceof PsiParenthesizedExpression) {
          final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)rootExpression;
          rootExpression = parenthesizedExpression.getExpression();
        }
        if (rootExpression instanceof PsiLiteralExpression || PsiUtil.isConstantExpression(rootExpression)) {
          return;
        }
      }
      final PsiType expressionType = expression.getType();
      if (expressionType == null || !ClassUtils.isPrimitiveNumericType(expressionType)) {
        return;
      }
      if (PsiType.CHAR.equals(expressionType) && (ignoreCharConversions || isArgumentOfStringIndexOf(parent))) {
        return;
      }
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
      if (!ClassUtils.isPrimitiveNumericType(expectedType)) {
        return;
      }
      if (expressionType.equals(expectedType)) {
        return;
      }
      if (ignoreWideningConversions && !TypeUtils.isNarrowingConversion(expressionType, expectedType)) {
        return;
      }
      if (ignoreCharConversions && PsiType.CHAR.equals(expectedType)) {
        return;
      }
      registerError(expression, expression, expressionType, expectedType);
    }

    private boolean isArgumentOfStringIndexOf(PsiElement parent) {
      if (!(parent instanceof PsiExpressionList)) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.INDEX_OF.equals(methodName) && !HardcodedMethodConstants.LAST_INDEX_OF.equals(methodName)) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String className = aClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_STRING.equals(className);
    }
  }
}
