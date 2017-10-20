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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
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
    return new BaseEqualsVisitor() {
      void checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType leftType, @NotNull PsiType rightType) {
        boolean convertible = TypeUtils.areConvertible(leftType, rightType);
        if (convertible) {
          if (!WARN_IF_NO_MUTUAL_SUBCLASS_FOUND) return;
          if (leftType.isAssignableFrom(rightType) || rightType.isAssignableFrom(leftType)) return;
          PsiClass leftClass = PsiUtil.resolveClassInClassTypeOnly(leftType);
          PsiClass rightClass = PsiUtil.resolveClassInClassTypeOnly(rightType);
          if (leftClass == null || rightClass == null) return;
          if (!leftClass.isInterface() && !rightClass.isInterface()) return;
          if (!rightClass.isInterface()) {
            PsiClass tmp = leftClass;
            leftClass = rightClass;
            rightClass = tmp;
          }
          if (InheritanceUtil.existsMutualSubclass(leftClass, rightClass, isOnTheFly())) return;
        }
        PsiElement name = expression.getReferenceNameElement();
        registerError(name == null ? expression : name, leftType, rightType, convertible);
      }
    };
  }
}