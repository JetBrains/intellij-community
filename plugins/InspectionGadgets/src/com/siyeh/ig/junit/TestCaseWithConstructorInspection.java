/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class TestCaseWithConstructorInspection extends ClassInspection {

  public String getID() {
    return "JUnitTestCaseWithNonTrivialConstructors";
  }

  public String getGroupDisplayName() {
    return GroupNames.JUNIT_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new TestCaseWithConstructorVisitor();
  }

  private static class TestCaseWithConstructorVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (!method.isConstructor()) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (isTrivial(method)) {
        return;
      }
      if (!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
        return;
      }
      registerMethodError(method);
    }

    private static boolean isTrivial(PsiMethod method) {
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return true;
      }
      final PsiStatement[] statements = body.getStatements();
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
      final PsiExpression expression =
        ((PsiExpressionStatement)statement).getExpression();
      if (expression == null) {
        return false;
      }
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression call =
        (PsiMethodCallExpression)expression;
      final PsiReferenceExpression ref = call.getMethodExpression();
      if (ref == null) {
        return false;
      }
      final String text = ref.getText();
      return PsiKeyword.SUPER.equals(text);
    }

  }
}
