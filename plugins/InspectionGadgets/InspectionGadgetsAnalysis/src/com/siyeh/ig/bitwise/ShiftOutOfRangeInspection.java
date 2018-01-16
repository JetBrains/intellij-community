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
package com.siyeh.ig.bitwise;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ShiftOutOfRangeInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "shift.operation.by.inappropriate.constant.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer value = (Integer)infos[0];
    if (value.intValue() > 0) {
      return InspectionGadgetsBundle.message(
        "shift.operation.by.inappropriate.constant.problem.descriptor.too.large");
    }
    else {
      return InspectionGadgetsBundle.message(
        "shift.operation.by.inappropriate.constant.problem.descriptor.negative");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ShiftOutOfRangeFix(((Integer)infos[0]).intValue(),
                                  ((Boolean)infos[1]).booleanValue());
  }

  private static class ShiftOutOfRangeFix extends InspectionGadgetsFix {

    private final int value;
    private final boolean isLong;

    ShiftOutOfRangeFix(int value, boolean isLong) {
      this.value = value;
      this.isLong = isLong;
    }

    @Override
    @NotNull
    public String getName() {
      final int newValue = isLong ? value & 63 : value & 31;
      return InspectionGadgetsBundle.message("shift.out.of.range.quickfix", Integer.valueOf(value), Integer.valueOf(newValue));
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Fix shift value";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)parent;
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final String text;
      final PsiExpression lhs = binaryExpression.getLOperand();
      if (PsiType.LONG.equals(lhs.getType())) {
        text = String.valueOf(value & 63);
      }
      else {
        text = String.valueOf(value & 31);
      }
      final PsiExpression newExpression =
        factory.createExpressionFromText(
          text, element);
      rhs.replace(newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ShiftOutOfRange();
  }

  private static class ShiftOutOfRange extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiJavaToken sign = expression.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      if (!tokenType.equals(JavaTokenType.LTLT) &&
          !tokenType.equals(JavaTokenType.GTGT) &&
          !tokenType.equals(JavaTokenType.GTGTGT)) {
        return;
      }
      final PsiType expressionType = expression.getType();
      if (expressionType == null) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      if (!PsiUtil.isConstantExpression(rhs)) {
        return;
      }
      final Integer valueObject =
        (Integer)ConstantExpressionUtil.computeCastTo(rhs,
                                                      PsiType.INT);
      if (valueObject == null) {
        return;
      }
      final int value = valueObject.intValue();
      if (expressionType.equals(PsiType.LONG)) {
        if (value < 0 || value > 63) {
          registerError(sign, valueObject, Boolean.TRUE);
        }
      }
      if (expressionType.equals(PsiType.INT)) {
        if (value < 0 || value > 31) {
          registerError(sign, valueObject, Boolean.FALSE);
        }
      }
    }
  }
}