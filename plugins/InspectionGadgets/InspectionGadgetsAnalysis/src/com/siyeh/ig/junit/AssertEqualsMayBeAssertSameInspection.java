// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      final PsiExpression argument1 = assertHint.getFirstArgument();
      if (!couldBeAssertSameArgument(argument1)) {
        return;
      }
      final PsiExpression argument2 = assertHint.getSecondArgument();
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
