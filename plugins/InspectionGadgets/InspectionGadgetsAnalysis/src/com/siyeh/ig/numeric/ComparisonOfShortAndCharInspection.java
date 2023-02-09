/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;

public class ComparisonOfShortAndCharInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("comparison.of.short.and.char.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ComparisonOfShortAndCharVisitor();
  }

  private static class ComparisonOfShortAndCharVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiType lhsType = lhs.getType();
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiType rhsType = rhs.getType();
      if (PsiTypes.shortType().equals(lhsType) && PsiTypes.charType().equals(rhsType)) {
        registerError(expression);
      }
      else if (PsiTypes.charType().equals(lhsType) && PsiTypes.shortType().equals(rhsType)) {
        registerError(expression);
      }
    }
  }
}
