/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestCaseWithConstructorInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "JUnitTestCaseWithNonTrivialConstructors";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "test.case.with.constructor.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (Boolean.TRUE.equals(infos[0])) {
      return InspectionGadgetsBundle.message(
        "test.case.with.constructor.problem.descriptor.initializer");
    }
    else {
      return InspectionGadgetsBundle.message(
        "test.case.with.constructor.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TestCaseWithConstructorVisitor();
  }

  private static class TestCaseWithConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!method.isConstructor()) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (!TestUtils.isJUnitTestClass(aClass)) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (isTrivial(body)) {
        return;
      }
      registerMethodError(method, Boolean.FALSE);
    }

    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass aClass = initializer.getContainingClass();
      if (!TestUtils.isJUnitTestClass(aClass)) {
        return;
      }
      registerClassInitializerError(initializer, Boolean.TRUE);
    }

    private static boolean isTrivial(@Nullable PsiCodeBlock codeBlock) {
      if (codeBlock == null) {
        return true;
      }
      final PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length == 0) {
        return true;
      }
      if (statements.length > 1) {
        return false;
      }
      final PsiStatement statement = statements[0];
      if (!(statement instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)statement;
      final PsiExpression expression =
        expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final String text = methodExpression.getText();
      return PsiKeyword.SUPER.equals(text);
    }
  }
}