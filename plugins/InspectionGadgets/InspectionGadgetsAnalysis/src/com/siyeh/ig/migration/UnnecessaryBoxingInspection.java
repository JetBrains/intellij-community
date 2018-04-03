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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
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

public class UnnecessaryBoxingInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyReportSuperfluouslyBoxed = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.boxing.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unnecessary.boxing.superfluous.option"),
                                          this, "onlyReportSuperfluouslyBoxed");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.boxing.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryBoxingFix();
  }

  private static class UnnecessaryBoxingFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, ProblemDescriptor descriptor) {
      final PsiCallExpression expression = (PsiCallExpression)descriptor.getPsiElement();
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression unboxedExpression = arguments[0];
      final Object value = ExpressionUtils.computeConstantExpression(unboxedExpression);
      CommentTracker commentTracker = new CommentTracker();
      if (value != null) {
        if (value == Boolean.TRUE) {
          PsiReplacementUtil.replaceExpression(expression, "java.lang.Boolean.TRUE", commentTracker);
          return;
        }
        else if (value == Boolean.FALSE) {
          PsiReplacementUtil.replaceExpression(expression, "java.lang.Boolean.FALSE", commentTracker);
          return;
        }
      }
      final String replacementText = getUnboxedExpressionText(unboxedExpression, expression, commentTracker);
      if (replacementText == null) {
        return;
      }
      PsiReplacementUtil.replaceExpression(expression, replacementText, commentTracker);
    }

    @Nullable
    private static String getUnboxedExpressionText(@NotNull PsiExpression unboxedExpression,
                                                   @NotNull PsiExpression boxedExpression,
                                                   CommentTracker commentTracker) {
      final PsiType boxedType = boxedExpression.getType();
      if (boxedType == null) {
        return null;
      }
      final PsiType expressionType = unboxedExpression.getType();
      if (expressionType == null) {
        return null;
      }
      final PsiType unboxedType = PsiPrimitiveType.getUnboxedType(boxedType);
      if (unboxedType == null) {
        return null;
      }
      final String text = commentTracker.text(unboxedExpression);
      if (expressionType.equals(unboxedType)) {
        final PsiElement parent = boxedExpression.getParent();
        if (parent instanceof PsiExpression && ParenthesesUtils.areParenthesesNeeded(unboxedExpression, (PsiExpression)parent, false)) {
          return '(' + text + ')';
        }
        else {
          return text;
        }
      }
      if (unboxedExpression instanceof PsiLiteralExpression) {
        if (unboxedType.equals(PsiType.LONG) && expressionType.equals(PsiType.INT)) {
          return text + 'L';
        }
        else if (unboxedType.equals(PsiType.FLOAT) && (expressionType.equals(PsiType.INT) || (expressionType.equals(PsiType.DOUBLE)) &&
                                                                                             !StringUtil.endsWithIgnoreCase(text, "d"))) {
          return text + 'f';
        }
        else if (unboxedType.equals(PsiType.DOUBLE) && expressionType.equals(PsiType.INT)) {
          return text + 'd';
        }
      }
      if (ParenthesesUtils.getPrecedence(unboxedExpression) > ParenthesesUtils.TYPE_CAST_PRECEDENCE) {
        return '(' + unboxedType.getCanonicalText() + ")(" + text + ')';
      }
      else {
        return '(' + unboxedType.getCanonicalText() + ')' + text;
      }
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryBoxingVisitor();
  }

  private class UnnecessaryBoxingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiType constructorType = expression.getType();
      final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(constructorType);
      if (unboxedType == null) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression boxedExpression = arguments[0];
      final PsiType argumentType = boxedExpression.getType();
      if (!(argumentType instanceof PsiPrimitiveType) || isBoxingNecessary(expression, boxedExpression)) {
        return;
      }
      if (onlyReportSuperfluouslyBoxed) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
        if (!(expectedType instanceof PsiPrimitiveType)) {
          return;
        }
      }
      registerError(expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression boxedExpression = arguments[0];
      if (!(boxedExpression.getType() instanceof PsiPrimitiveType)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls
      final String referenceName = methodExpression.getReferenceName();
      if (!"valueOf".equals(referenceName)) {
        return;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
      final String canonicalText = referenceExpression.getCanonicalText();
      if (PsiTypesUtil.unboxIfPossible(canonicalText) == canonicalText || isBoxingNecessary(expression, boxedExpression)) {
        return;
      }
      if (onlyReportSuperfluouslyBoxed) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
        if (!(expectedType instanceof PsiPrimitiveType)) {
          return;
        }
      }
      registerError(expression);
    }

    private boolean isBoxingNecessary(PsiExpression boxingExpression, PsiExpression boxedExpression) {
      PsiElement parent = boxingExpression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        boxingExpression = (PsiExpression)parent;
        parent = parent.getParent();
      }
      if (parent instanceof PsiExpressionStatement || parent instanceof PsiReferenceExpression) {
        return true;
      }
      else if (parent instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression castExpression = (PsiTypeCastExpression)parent;
        return TypeUtils.isTypeParameter(castExpression.getType());
      }
      else if (parent instanceof PsiConditionalExpression) {
        final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)parent;
        final PsiExpression thenExpression = conditionalExpression.getThenExpression();
        final PsiExpression elseExpression = conditionalExpression.getElseExpression();
        if (elseExpression == null || thenExpression == null) {
          return true;
        }
        if (PsiTreeUtil.isAncestor(thenExpression, boxingExpression, false)) {
          final PsiType type = elseExpression.getType();
          return !(type instanceof PsiPrimitiveType);
        }
        else if (PsiTreeUtil.isAncestor(elseExpression, boxingExpression, false)) {
          final PsiType type = thenExpression.getType();
          return !(type instanceof PsiPrimitiveType);
        }
        else {
          return false;
        }
      }
      else if (parent instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
        return isPossibleObjectComparison(boxingExpression, polyadicExpression);
      }
      return MethodCallUtils.isNecessaryForSurroundingMethodCall(boxingExpression, boxedExpression) ||
             !LambdaUtil.isSafeLambdaReturnValueReplacement(boxingExpression, boxedExpression);
    }

    private boolean isPossibleObjectComparison(PsiExpression expression, PsiPolyadicExpression polyadicExpression) {
      if (!ComparisonUtils.isEqualityComparison(polyadicExpression)) {
        return false;
      }
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (operand == expression) {
          continue;
        }
        if (!(operand.getType() instanceof PsiPrimitiveType)) {
          return true;
        }
        //else if (isUnboxingExpression(operand)) {
        //  return true;
        //}
      }
      return false;
    }
  }
}