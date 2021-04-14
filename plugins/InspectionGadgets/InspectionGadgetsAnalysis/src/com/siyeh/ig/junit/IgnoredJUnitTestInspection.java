// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IgnoredJUnitTestInspection extends BaseInspection {

  public boolean onlyReportWithoutReason = true;

  @Override
  public @Nullable JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("ignored.junit.test.ignore.reason.option"),
                                          this, "onlyReportWithoutReason");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiNamedElement info = (PsiNamedElement)infos[0];
    if (info instanceof PsiClass) {
      return InspectionGadgetsBundle.message("ignored.junit.test.classproblem.descriptor", info.getName());
    }
    else {
      return InspectionGadgetsBundle.message("ignored.junit.test.method.problem.descriptor", info.getName());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IgnoredJUnitTestVisitor();
  }

  private class IgnoredJUnitTestVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      final PsiModifierListOwner modifierListOwner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
      if (!(modifierListOwner instanceof PsiClass || modifierListOwner instanceof PsiMethod)) {
        return;
      }
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      if (nameReferenceElement == null) {
        return;
      }
      final PsiElement target = nameReferenceElement.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      @NonNls final String qualifiedName = aClass.getQualifiedName();
      if (!"org.junit.Ignore".equals(qualifiedName) && !"org.junit.jupiter.api.Disabled".equals(qualifiedName)) {
        return;
      }
      final PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, null);
      if (attribute != null) {
        final PsiAnnotationMemberValue value = attribute.getValue();
        if (onlyReportWithoutReason && value instanceof PsiExpression &&
            ExpressionUtils.computeConstantExpression((PsiExpression)value) instanceof String) {
          return;
        }
      }
      registerError(nameReferenceElement, modifierListOwner);
    }
  }
}
