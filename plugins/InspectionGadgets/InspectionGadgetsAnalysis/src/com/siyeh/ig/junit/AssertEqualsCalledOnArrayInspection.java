// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.testFrameworks.AssertHint;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AssertEqualsCalledOnArrayInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assertequals.called.on.arrays.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceAssertEqualsFix("assertArrayEquals");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsOnArrayVisitor();
  }

  private static class AssertEqualsOnArrayVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final AssertHint assertHint = AssertHint.createAssertEqualsHint(expression);
      if (assertHint == null) {
        return;
      }

      //no assertArrayEquals for testng
      PsiClass containingClass = Objects.requireNonNull(Objects.requireNonNull(expression.resolveMethod()).getContainingClass());
      if (!Objects.requireNonNull(containingClass.getQualifiedName()).contains("junit")) {
        return;
      }

      final PsiType type1 = assertHint.getFirstArgument().getType();
      final PsiType type2 = assertHint.getSecondArgument().getType();
      if (!(type1 instanceof PsiArrayType) || !(type2 instanceof PsiArrayType)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
