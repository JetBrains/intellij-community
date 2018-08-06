// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class CallToSimpleGetterInClassInspectionBase extends BaseInspection {
  @SuppressWarnings("UnusedDeclaration")
  public boolean ignoreGetterCallsOnOtherObjects = false;
  @SuppressWarnings("UnusedDeclaration")
  public boolean onlyReportPrivateGetter = false;

  @Override
  @NotNull
  public String getID() {
    return "CallToSimpleGetterFromWithinClass";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("call.to.simple.getter.in.class.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("call.to.simple.getter.in.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToSimpleGetterInClassVisitor();
  }

  private class CallToSimpleGetterInClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);

      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (referenceName == null ||
          PropertyUtilBase.getMethodNameGetterFlavour(referenceName) == PropertyUtilBase.GetterFlavour.NOT_A_GETTER) {
        return;
      }

      final PsiElement parent = call.getParent();
      if (parent instanceof PsiExpressionStatement) {
        // inlining a top-level getter call would break code
        return;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(call);
      if (containingClass == null) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      if (!containingClass.equals(method.getContainingClass())) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
        if (ignoreGetterCallsOnOtherObjects) {
          return;
        }
        final PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
        if (!containingClass.equals(qualifierClass)) {
          return;
        }
      }
      final PsiField field = PropertyUtil.getFieldOfGetter(method);
      if (field == null) {
        return;
      }
      final PsiMember member = PsiTreeUtil.getParentOfType(call, PsiMember.class);
      if (member instanceof PsiField && !(member instanceof PsiEnumConstant) && member.getTextOffset() < field.getTextOffset()) {
        return;
      }
      if (onlyReportPrivateGetter && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiMethod overridingMethod = OverridingMethodsSearch.search(method).findFirst();
      if (overridingMethod != null) {
        return;
      }
      registerMethodCallError(call);
    }
  }
}
