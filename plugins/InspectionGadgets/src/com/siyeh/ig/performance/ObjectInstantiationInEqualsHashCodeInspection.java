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
    if (infos.length > 1) {
      return InspectionGadgetsBundle.message("object.instantiation.inside.equals.or.hashcode.problem.descriptor2", method.getName(), infos[1]);
    }
    return InspectionGadgetsBundle.message("object.instantiation.inside.equals.or.hashcode.problem.descriptor", method.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectInstantiationInEqualsHashCodeVisitor();
  }

  private static class ObjectInstantiationInEqualsHashCodeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitExpression(PsiExpression expression) {
      if (!ExpressionUtils.isAutoBoxed(expression) || !isInsideEqualsOrHashCode(expression)) {
        return;
      }
      registerError(expression, expression, "autoboxing");
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null || iteratedValue.getType() instanceof PsiArrayType || !isInsideEqualsOrHashCode(statement)) {
        return;
      }
      registerError(iteratedValue, iteratedValue, "iterator");
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      if (method.isVarArgs()) {
        if (!isInsideEqualsOrHashCode(expression)) {
          return;
        }
        registerError(expression, expression, "varargs call");
      }
      else {
        final String name = methodExpression.getReferenceName();
        if (!"valueOf".equals(name)) {
          return;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length != 1) {
          return;
        }
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
          return;
        }
        final String qualifiedName = aClass.getQualifiedName();
        if (!CommonClassNames.JAVA_LANG_SHORT.equals(qualifiedName) && !CommonClassNames.JAVA_LANG_INTEGER.equals(qualifiedName) &&
            !CommonClassNames.JAVA_LANG_LONG.equals(qualifiedName) && !CommonClassNames.JAVA_LANG_DOUBLE.equals(qualifiedName) &&
            !CommonClassNames.JAVA_LANG_FLOAT.equals(qualifiedName) && !CommonClassNames.JAVA_LANG_CHARACTER.equals(qualifiedName)) {
          return;
        }
        if (!isInsideEqualsOrHashCode(expression)) {
          return;
        }
        registerError(expression, expression);
      }
    }

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      if (!(expression.getParent() instanceof PsiVariable)) {
        // new expressions are already reported.
        return;
      }
      if (!isInsideEqualsOrHashCode(expression)) {
        return;
      }
      registerError(expression, expression);
    }

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
