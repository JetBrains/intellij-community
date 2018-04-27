/*
 * Copyright 2008-2018 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EqualsToEqualityFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.EqualityCheck;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class EqualsCalledOnEnumConstantInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("equals.called.on.enum.constant.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.called.on.enum.constant.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement element = (PsiElement)infos[0];
    final boolean negated = (boolean)infos[1];
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      return null;
    }
    return new EqualsToEqualityFix(negated);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsCalledOnEnumValueVisitor();
  }

  private static class EqualsCalledOnEnumValueVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      EqualityCheck check = EqualityCheck.from(expression);
      if (check == null) return;
      final PsiExpression left = check.getLeft();
      if (!TypeUtils.expressionHasTypeOrSubtype(left, CommonClassNames.JAVA_LANG_ENUM)) return;
      final PsiExpression right = check.getRight();

      final PsiType comparedTypeErasure = TypeConversionUtil.erasure(left.getType());
      final PsiType comparisonTypeErasure = TypeConversionUtil.erasure(right.getType());
      if (comparedTypeErasure == null || comparisonTypeErasure == null ||
          !TypeConversionUtil.areTypesConvertible(comparedTypeErasure, comparisonTypeErasure)) {
        return;
      }
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      final boolean negated = parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent);
      registerMethodCallError(expression, expression, negated);
    }
  }
}
