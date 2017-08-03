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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class EqualsBetweenInconvertibleTypesInspection extends BaseInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "equals.between.inconvertible.types.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType comparedType = (PsiType)infos[0];
    final PsiType comparisonType = (PsiType)infos[1];
    return InspectionGadgetsBundle.message(
      "equals.between.inconvertible.types.problem.descriptor",
      comparedType.getPresentableText(),
      comparisonType.getPresentableText());
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseEqualsVisitor() {
      void checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType leftType, @NotNull PsiType rightType) {
        if (!TypeUtils.areConvertible(leftType, rightType)) {
          PsiElement name = expression.getReferenceNameElement();
          registerError(name == null ? expression : name, leftType, rightType);
        }
      }
    };
  }
}