/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.InlineGetterSetterCallFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class CallToSimpleSetterInClassInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings("UnusedDeclaration")
  public boolean ignoreSetterCallsOnOtherObjects = false;
  @SuppressWarnings("UnusedDeclaration")
  public boolean onlyReportPrivateSetter = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSetterCallsOnOtherObjects", InspectionGadgetsBundle.message("call.to.simple.setter.in.class.ignore.option")),
      checkbox("onlyReportPrivateSetter", InspectionGadgetsBundle.message("call.to.private.setter.in.class.option")));
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new InlineGetterSetterCallFix(false);
  }

  @Override
  public boolean runForWholeFile() {
    // Changes in another method (making setter more complicated) may affect 
    // the inspection result at call sites
    return true;
  }

  @Override
  @NotNull
  public String getID() {
    return "CallToSimpleSetterFromWithinClass";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("call.to.simple.setter.in.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToSimpleSetterInClassVisitor();
  }

  private class CallToSimpleSetterInClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
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
        if (ignoreSetterCallsOnOtherObjects) {
          return;
        }
        final PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
        if (!containingClass.equals(qualifierClass)) {
          return;
        }
      }
      if (!PropertyUtil.isSimpleSetter(method)) {
        return;
      }
      if (onlyReportPrivateSetter && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
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