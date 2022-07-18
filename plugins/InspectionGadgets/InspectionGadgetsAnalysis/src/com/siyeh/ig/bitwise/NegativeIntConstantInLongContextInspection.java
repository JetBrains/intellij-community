/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bitwise;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class NegativeIntConstantInLongContextInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression literal) {
        if (!isNegativeHexLiteral(literal)) return;
        checkLongContext(literal);
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
        PsiVariable variable = tryCast(ref.resolve(), PsiVariable.class);
        if (variable == null || !variable.hasModifierProperty(PsiModifier.FINAL)) return;
        PsiLiteralExpression initializer =
          tryCast(PsiUtil.skipParenthesizedExprDown(PsiFieldImpl.getDetachedInitializer(variable)), PsiLiteralExpression.class);
        if (initializer == null || !isNegativeHexLiteral(initializer)) return;
        checkLongContext(ref);
      }

      private void checkLongContext(@NotNull PsiExpression expression) {
        if (!PsiType.LONG.equals(ExpectedTypeUtils.findExpectedType(expression, true))) return;
        if (isInAssertEqualsLong(expression)) return;
        holder.registerProblem(expression, InspectionGadgetsBundle.message("negative.int.constant.in.long.context.display.name"));
      }
    };
  }

  private static boolean isInAssertEqualsLong(PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (!(parent instanceof PsiExpressionList)) return false;
    PsiMethodCallExpression call = tryCast(parent.getParent(), PsiMethodCallExpression.class);
    if (call == null) return false;
    String name = call.getMethodExpression().getReferenceName();
    if (!"assertEquals".equals(name)) return false;
    PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
    return ContainerUtil.exists(args, arg -> !PsiTreeUtil.isAncestor(arg, expression, false) && PsiType.INT.equals(arg.getType()));
  }

  private static boolean isNegativeHexLiteral(@NotNull PsiLiteralExpression literal) {
    if (!PsiType.INT.equals(literal.getType())) return false;
    if (!literal.getText().startsWith("0x")) return false;
    Integer value = tryCast(literal.getValue(), Integer.class);
    return value != null && value < 0;
  }
}