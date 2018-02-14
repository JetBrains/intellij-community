/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
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

      String referenceName = call.getMethodExpression().getReferenceName();
      if (referenceName == null ||
          PropertyUtilBase.getMethodNameGetterFlavour(referenceName) == PropertyUtilBase.GetterFlavour.NOT_A_GETTER) {
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
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
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
      if (!PropertyUtil.isSimpleGetter(method)) {
        return;
      }
      if (onlyReportPrivateGetter && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final Query<PsiMethod> query = OverridingMethodsSearch.search(method);
      final PsiMethod overridingMethod = query.findFirst();
      if (overridingMethod != null) {
        return;
      }
      registerMethodCallError(call);
    }
  }
}
