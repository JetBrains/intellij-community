/*
 * Copyright 2010-2012 Bas Leijdekkers
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
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final int argIndex = assertHint.getArgIndex();
      final PsiType type1 = arguments[argIndex].getType();
      final PsiType type2 = arguments[argIndex + 1].getType();
      if (!(type1 instanceof PsiArrayType) || !(type2 instanceof PsiArrayType)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
