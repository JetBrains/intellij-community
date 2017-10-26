/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InheritanceUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EqualsBetweenInconvertibleTypesInspection extends BaseInspection {

  public boolean WARN_IF_NO_MUTUAL_SUBCLASS_FOUND = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "equals.between.inconvertible.types.display.name");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("equals.between.inconvertible.types.mutual.subclass.option"),
                                          this, "WARN_IF_NO_MUTUAL_SUBCLASS_FOUND");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType comparedType = (PsiType)infos[0];
    final PsiType comparisonType = (PsiType)infos[1];
    final boolean convertible = (boolean)infos[2];
    if (convertible) {
      return InspectionGadgetsBundle.message(
        "equals.between.inconvertible.types.no.mutual.subclass.problem.descriptor",
        comparedType.getPresentableText(),
        comparisonType.getPresentableText());
    }
    else {
      return InspectionGadgetsBundle.message(
        "equals.between.inconvertible.types.problem.descriptor",
        comparedType.getPresentableText(),
        comparisonType.getPresentableText());
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsBetweenInconvertibleTypesVisitor();
  }

  private class EqualsBetweenInconvertibleTypesVisitor extends BaseEqualsVisitor {

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!WARN_IF_NO_MUTUAL_SUBCLASS_FOUND) return;
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQEQ) && !tokenType.equals(JavaTokenType.NE)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiType lhsType = lhs.getType();
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiType rhsType = rhs.getType();
      if (lhsType == null || rhsType == null || !TypeUtils.areConvertible(lhsType, rhsType)) {
        // red code
        return;
      }
      if (existsSharedSubclass(lhsType, rhsType)) {
        return;
      }
      registerError(expression.getOperationSign(), lhsType, rhsType, true);
    }

    void checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType leftType, @NotNull PsiType rightType) {
      boolean convertible = TypeUtils.areConvertible(leftType, rightType);
      if (convertible && (!WARN_IF_NO_MUTUAL_SUBCLASS_FOUND || existsSharedSubclass(leftType, rightType))) return;
      if (TypeUtils.mayBeEqualByContract(leftType, rightType)) return;
      PsiElement name = expression.getReferenceNameElement();
      registerError(name == null ? expression : name, leftType, rightType, convertible);
    }

    private boolean existsSharedSubclass(@NotNull PsiType leftType, @NotNull PsiType rightType) {
      if (leftType.isAssignableFrom(rightType) || rightType.isAssignableFrom(leftType)) return true;
      PsiClass leftClass = PsiUtil.resolveClassInClassTypeOnly(leftType);
      PsiClass rightClass = PsiUtil.resolveClassInClassTypeOnly(rightType);
      if (leftClass == null || rightClass == null) return true;
      if (!rightClass.isInterface()) {
        PsiClass tmp = leftClass;
        leftClass = rightClass;
        rightClass = tmp;
      }
      return InheritanceUtil.existsMutualSubclass(leftClass, rightClass, isOnTheFly());
    }
  }
}