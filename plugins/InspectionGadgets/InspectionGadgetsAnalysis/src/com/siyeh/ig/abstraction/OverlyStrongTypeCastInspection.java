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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
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

public class OverlyStrongTypeCastInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreInMatchingInstanceof = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("overly.strong.type.cast.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiType expectedType = (PsiType)infos[0];
    final String typeText = expectedType.getPresentableText();
    return InspectionGadgetsBundle.message("overly.strong.type.cast.problem.descriptor", typeText);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("overly.strong.type.cast.ignore.in.matching.instanceof.option"),
                                          this, "ignoreInMatchingInstanceof");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new OverlyStrongCastFix();
  }

  private static class OverlyStrongCastFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("overly.strong.type.cast.weaken.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement castTypeElement = descriptor.getPsiElement();
      final PsiTypeCastExpression expression = (PsiTypeCastExpression)castTypeElement.getParent();
      if (expression == null) {
        return;
      }
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
      if (expectedType == null) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      @NonNls
      final String newExpression = '(' + expectedType.getCanonicalText() + ')' + commentTracker.text(operand);
      PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpression, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverlyStrongTypeCastVisitor();
  }

  private class OverlyStrongTypeCastVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType operandType = operand.getType();
      if (operandType == null) {
        return;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
      if (expectedType == null || expectedType.equals(type)) {
        return;
      }
      final PsiClass resolved = PsiUtil.resolveClassInType(expectedType);
      if (resolved != null && !resolved.isPhysical()) {
        return;
      }
      if (expectedType.isAssignableFrom(operandType) && !MethodCallUtils.isNecessaryForSurroundingMethodCall(expression, operand)) {
        //then it's redundant, and caught by the "Redundant type cast" inspection
        return;
      }
      if (TypeUtils.isTypeParameter(expectedType)) {
        return;
      }
      if (expectedType instanceof PsiArrayType) {
        final PsiArrayType arrayType = (PsiArrayType)expectedType;
        final PsiType componentType = arrayType.getDeepComponentType();
        if (TypeUtils.isTypeParameter(componentType)) {
          return;
        }
      }
      if (type instanceof PsiPrimitiveType || expectedType instanceof PsiPrimitiveType) {
        return;
      }
      if (PsiPrimitiveType.getUnboxedType(type) != null || PsiPrimitiveType.getUnboxedType(expectedType) != null) {
        return;
      }
      if (expectedType instanceof PsiClassType) {
        final PsiClassType expectedClassType = (PsiClassType)expectedType;
        final PsiClassType expectedRawType = expectedClassType.rawType();
        if (type.equals(expectedRawType)) {
          return;
        }
        if (type instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)type;
          final PsiClassType rawType = classType.rawType();
          if (rawType.equals(expectedRawType)) {
            return;
          }
        }
        if (type instanceof PsiArrayType) {
          return;
        }
      }
      if (ignoreInMatchingInstanceof && InstanceOfUtils.hasAgreeingInstanceof(expression)) {
        return;
      }
      final PsiTypeElement castTypeElement = expression.getCastType();
      if (castTypeElement == null) {
        return;
      }
      if (operand instanceof PsiFunctionalExpression && !LambdaUtil.isFunctionalType(expectedType)) {
        return;
      }
      registerError(castTypeElement, expectedType);
    }
  }
}