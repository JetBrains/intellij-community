/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ObjectInstantiationInEqualsHashCodeInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("object.instantiation.inside.equals.or.hashcode.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiMethod method = PsiTreeUtil.getParentOfType((PsiElement)infos[0], PsiMethod.class);
    assert method != null;
    return InspectionGadgetsBundle.message("object.instantiation.inside.equals.or.hashcode.problem.descriptor", method.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectInstantiationInEqualsHashCodeVisitor();
  }

  // todo check boxing too
  private static class ObjectInstantiationInEqualsHashCodeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      if (!ExpressionUtils.hasStringType(expression) || ExpressionUtils.isEvaluatedAtCompileTime(expression)) {
        return;
      }
      if (!isInsideEqualsOrHashCode(expression)) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isInsideEqualsOrHashCode(expression)) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }

    private static boolean isInsideEqualsOrHashCode(PsiElement element) {
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiAssertStatement.class, PsiThrowStatement.class);
      if (method == null) {
        return false;
      }
      return MethodUtils.isEquals(method) || MethodUtils.isHashCode(method) ||
             MethodUtils.isCompareTo(method) || MethodUtils.isComparatorCompare(method);
    }
  }
}
