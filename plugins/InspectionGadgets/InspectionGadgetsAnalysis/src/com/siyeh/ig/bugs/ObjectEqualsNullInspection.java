/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EqualsToEqualityFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ObjectEqualsNullInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("object.equals.null.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("object.equals.null.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean negated = (Boolean)infos[0];
    return negated == null ? null : new EqualsToEqualityFix(negated.booleanValue());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectEqualsNullVisitor();
  }

  private static class ObjectEqualsNullVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      if (!MethodCallUtils.isEqualsCall(call)) {
        return;
      }
      PsiExpression[] args = call.getArgumentList().getExpressions();
      final PsiExpression argument = args.length > 0 ? args[0] : null;
      if (!ExpressionUtils.isNullLiteral(argument)) {
        return;
      }
      PsiElement parent = ParenthesesUtils.getParentSkipParentheses(call);
      final boolean negated = parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent);
      if (negated) {
        parent = parent.getParent();
      }
      final boolean quickFix = !(parent instanceof PsiExpressionStatement);
      registerError(argument, quickFix ? Boolean.valueOf(negated) : null);
    }
  }
}