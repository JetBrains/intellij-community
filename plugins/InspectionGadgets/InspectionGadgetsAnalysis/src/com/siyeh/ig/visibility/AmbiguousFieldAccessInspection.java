// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AmbiguousFieldAccessInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiClass fieldClass = (PsiClass)infos[0];
    final PsiVariable variable = (PsiVariable)infos[1];
    if (variable instanceof PsiLocalVariable) {
      return InspectionGadgetsBundle.message("ambiguous.field.access.hides.local.variable.problem.descriptor", fieldClass.getName());
    }
    else if (variable instanceof PsiParameter) {
      return InspectionGadgetsBundle.message("ambiguous.field.access.hides.parameter.problem.descriptor", fieldClass.getName());
    }
    else {
      return InspectionGadgetsBundle.message("ambiguous.field.access.hides.field.problem.descriptor", fieldClass.getName());
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new AmbiguousFieldAccessVisitor();
  }

  private static class AmbiguousFieldAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.isQualified()) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (target == null) {
        return;
      }
      if (!(target instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)target;
      final PsiClass fieldClass = field.getContainingClass();
      if (fieldClass == null) {
        return;
      }
      PsiClass containingClass = ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return;
      }
      if (!containingClass.isInheritor(fieldClass, true)) {
        return;
      }
      final PsiElement parent = containingClass.getParent();
      if (parent instanceof PsiFile) {
        return;
      }
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
      final String referenceText = expression.getText();
      final PsiVariable variable = resolveHelper.resolveAccessibleReferencedVariable(referenceText, parent);
      if (variable == null || field == variable) {
        return;
      }
      if (expression.advancedResolve(false).getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
        // is statically imported
        return;
      }
      registerError(expression, fieldClass, variable, isOnTheFly());
    }
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new AmbiguousFieldAccessFix();
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    boolean isOnTheFly = (boolean)infos[2];
    final PsiVariable variable = (PsiVariable)infos[1];
    if (isOnTheFly) {
      return new InspectionGadgetsFix[] { new NavigateToApparentlyAccessedElementFix(variable), new AmbiguousFieldAccessFix() };
    }
    else {
      return new InspectionGadgetsFix[] { new AmbiguousFieldAccessFix() };
    }
  }

  private static class AmbiguousFieldAccessFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("ambiguous.field.access.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      final String newExpressionText = "super." + referenceExpression.getText();
      PsiReplacementUtil.replaceExpression(referenceExpression, newExpressionText);
    }
  }

  private static class NavigateToApparentlyAccessedElementFix extends InspectionGadgetsFix {

    private final int type;

    NavigateToApparentlyAccessedElementFix(PsiVariable variable) {
      if (variable instanceof PsiLocalVariable) type = 1;
      else if (variable instanceof PsiParameter) type = 2;
      else type = 3;
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("ambiguous.field.access.navigate.quickfix", type);
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      PsiClass containingClass = ClassUtils.getContainingClass(element);
      if (containingClass == null) {
        return;
      }
      final PsiElement parent = containingClass.getParent();
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper();
      final String referenceText = element.getText();
      final PsiVariable variable = resolveHelper.resolveAccessibleReferencedVariable(referenceText, parent);
      if (variable != null) {
        variable.navigate(true);
      }
    }
  }
}