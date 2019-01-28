// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.testFrameworks;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.bugs.InconvertibleTypesChecker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public abstract class BaseAssertEqualsBetweenInconvertibleTypesInspection extends BaseInspection {
  protected abstract boolean checkTestNG();

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assertequals.between.inconvertible.types.display.name");
  }

  
  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType comparedType = (PsiType)infos[0];
    final PsiType comparisonType = (PsiType)infos[1];
    return InspectionGadgetsBundle.message("assertequals.between.inconvertible.types.problem.descriptor",
                                           StringUtil.escapeXmlEntities(comparedType.getPresentableText()),
                                           StringUtil.escapeXmlEntities(comparisonType.getPresentableText()));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsBetweenInconvertibleTypesVisitor();
  }

   @Override
  public boolean isEnabledByDefault() {
    return true;
  }
  
  private class AssertEqualsBetweenInconvertibleTypesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final AssertHint assertHint = AssertHint.createAssertEqualsHint(expression, checkTestNG());
      if (assertHint == null) return;
      final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      final int argIndex = assertHint.getArgIndex();
      final PsiType type1 = arguments[argIndex].getType();
      if (type1 == null) {
        return;
      }
      final PsiType type2 = arguments[argIndex + 1].getType();
      if (type2 == null) {
        return;
      }
      final PsiParameter[] parameters = assertHint.getMethod().getParameterList().getParameters();
      final PsiType parameterType1 = parameters[argIndex].getType();
      final PsiType parameterType2 = parameters[argIndex + 1].getType();
      final PsiClassType objectType = TypeUtils.getObjectType(expression);
      if (!objectType.equals(parameterType1) || !objectType.equals(parameterType2)) {
        return;
      }

      new InconvertibleTypesChecker() {
        @Override
        protected void registerEqualsError(PsiElement highlightLocation,
                                           @NotNull PsiType leftType,
                                           @NotNull PsiType rightType,
                                           boolean convertible) {
            AssertEqualsBetweenInconvertibleTypesVisitor.this.registerError(highlightLocation, leftType, rightType, convertible);
          }
        }.checkTypes(expression.getMethodExpression(), type1, type2, true, isOnTheFly());
    }
  }
}
