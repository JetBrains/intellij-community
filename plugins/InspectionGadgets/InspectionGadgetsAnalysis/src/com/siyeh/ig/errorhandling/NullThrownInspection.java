// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class NullThrownInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "null.thrown.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ThrowNullFix();
  }

  private static class ThrowNullFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", new NullPointerException());
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression newExpression = factory.createExpressionFromText("new java.lang.NullPointerException()", element);
      element.replace(newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowNullVisitor();
  }

  private static class ThrowNullVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception =
        PsiUtil.skipParenthesizedExprDown(statement.getException());
      if (!(exception instanceof PsiLiteralExpression)) {
        return;
      }
      final PsiType type = exception.getType();
      if (!PsiTypes.nullType().equals(type)) {
        return;
      }
      registerError(exception);
    }
  }
}
