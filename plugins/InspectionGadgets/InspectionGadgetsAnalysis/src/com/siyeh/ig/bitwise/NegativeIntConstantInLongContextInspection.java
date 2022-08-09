// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bitwise;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.LiteralFormatUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
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
        LocalQuickFix[] fixes = null;
        if (expression instanceof PsiLiteralExpression) {
          fixes = new LocalQuickFix[]{
            new AddLongSuffixFix(),
            new ConvertToLongFix()
          };
        }
        holder.registerProblem(expression, InspectionGadgetsBundle.message("negative.int.constant.in.long.context.display.name"), fixes);
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
    String text = literal.getText();
    if (!text.startsWith("0x") && !text.startsWith("0X")) return false;
    Integer value = tryCast(literal.getValue(), Integer.class);
    return value != null && value < 0;
  }

  private static class AddLongSuffixFix implements LocalQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("negative.int.constant.in.long.context.fix.add.suffix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLiteralExpression literal = tryCast(descriptor.getStartElement(), PsiLiteralExpression.class);
      if (literal == null) return;
      PsiType type = literal.getType();
      if (!PsiType.INT.equals(type)) return;
      new CommentTracker().replaceAndRestoreComments(literal, literal.getText() + "L");
    }
  }

  private static class ConvertToLongFix implements LocalQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("negative.int.constant.in.long.context.fix.convert");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLiteralExpression literal = tryCast(descriptor.getStartElement(), PsiLiteralExpression.class);
      if (literal == null) return;
      Integer value = tryCast(literal.getValue(), Integer.class);
      if (value == null || value >= 0) return;
      String longLiteral = Long.toHexString(value);
      String result = LiteralFormatUtil.format(literal.getText().substring(0, 2) + longLiteral + "L", PsiType.LONG);
      new CommentTracker().replaceAndRestoreComments(literal, result);
    }
  }
}