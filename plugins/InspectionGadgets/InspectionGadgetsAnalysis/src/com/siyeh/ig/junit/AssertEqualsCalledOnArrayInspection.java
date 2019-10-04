// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.testFrameworks.AssertHint;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AssertEqualsCalledOnArrayInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assertequals.called.on.arrays.display.name");
  }

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
      final AssertHint assertHint = AssertHint.createAssertEqualsHint(expression, false);
      if (assertHint == null) {
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
