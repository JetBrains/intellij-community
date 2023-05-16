// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddThisQualifierFix;
import org.jetbrains.annotations.NotNull;

public class UnqualifiedFieldAccessInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnqualifiedFieldAccessVisitor();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unqualified.field.access.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiReferenceExpression expressionToQualify = (PsiReferenceExpression)infos[0];
    final PsiField fieldAccessed = (PsiField)infos[1];
    return AddThisQualifierFix.buildFix(expressionToQualify, fieldAccessed);
  }

  private static class UnqualifiedFieldAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (qualifierExpression != null) {
        return;
      }
      final PsiReferenceParameterList parameterList = expression.getParameterList();
      if (parameterList == null) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiField field)) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerError(expression, expression, field);
    }
  }
}