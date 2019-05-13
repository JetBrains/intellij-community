/*
 * Copyright 2008-2017 Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.testFrameworks.AssertHint;
import org.jetbrains.annotations.NotNull;

public class AssertEqualsMayBeAssertSameInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assertequals.may.be.assertsame.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assertequals.may.be.assertsame.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceAssertEqualsFix("assertSame");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsMayBeAssertSameVisitor();
  }

  private static class AssertEqualsMayBeAssertSameVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final AssertHint assertHint = AssertHint.createAssertEqualsHint(expression, false);
      if (assertHint == null) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 3 && arguments.length != 2) {
        return;
      }
      final int argIndex = assertHint.getArgIndex();
      final PsiExpression argument1 = arguments[argIndex];
      if (!couldBeAssertSameArgument(argument1)) {
        return;
      }
      final PsiExpression argument2 = arguments[argIndex + 1];
      if (!couldBeAssertSameArgument(argument2)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean couldBeAssertSameArgument(PsiExpression expression) {
      final PsiClass argumentClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      if (argumentClass == null) {
        return false;
      }
      if (!argumentClass.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }
      final PsiMethod[] methods = argumentClass.findMethodsByName("equals", true);
      final PsiManager manager = expression.getManager();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(manager.getProject());
      final PsiClass objectClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, argumentClass.getResolveScope());
      if (objectClass == null) {
        return false;
      }
      for (PsiMethod method : methods) {
        final PsiClass containingClass = method.getContainingClass();
        if (!objectClass.equals(containingClass)) {
          return false;
        }
      }
      return true;
    }
  }
}
