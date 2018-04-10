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
package com.siyeh.ig.junit;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class SuperTearDownInFinallyInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("super.tear.down.in.finally.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("super.tear.down.in.finally.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuperTearDownInFinallyVisitor();
  }

  private static class SuperTearDownInFinallyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (!MethodCallUtils.hasSuperQualifier(expression)) return;

      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiMember.class, PsiLambdaExpression.class);
      if (method == null || !method.getName().equals("tearDown")) {
        return;
      }
      if (!MethodCallUtils.isSuperMethodCall(expression, method)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(containingClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE)) {
        return;
      }
      final PsiTryStatement tryStatement =
        PsiTreeUtil.getParentOfType(expression, PsiTryStatement.class, true, PsiMember.class, PsiLambdaExpression.class);
      if (tryStatement != null) {
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (PsiTreeUtil.isAncestor(finallyBlock, expression, true)) {
          return;
        }
      }
      if (!hasNonTrivialActivity(method, expression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean hasNonTrivialActivity(PsiMethod method, PsiElement ignore) {
      final NonTrivialActivityVisitor visitor = new NonTrivialActivityVisitor(ignore);
      method.accept(visitor);
      return visitor.hasNonTrivialActivity();
    }

    private static class NonTrivialActivityVisitor extends JavaRecursiveElementWalkingVisitor {

      private final PsiElement myIgnore;
      private boolean nonTrivialActivity = false;

      public NonTrivialActivityVisitor(PsiElement ignore) {
        myIgnore = ignore;
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        if (expression == myIgnore) {
          return;
        }
        nonTrivialActivity = true;
      }

      public boolean hasNonTrivialActivity() {
        return nonTrivialActivity;
      }
    }
  }
}
