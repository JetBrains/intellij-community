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
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeClassFinalFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OverridableMethodCallDuringObjectConstructionInspection extends
                                                                     BaseInspection {

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)infos[0];
    final PsiClass callClass = ClassUtils.getContainingClass(methodCallExpression);
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null || callClass == null || MethodUtils.isOverriddenInHierarchy(method, callClass)) {
      return InspectionGadgetsFix.EMPTY_ARRAY;
    }
    final List<InspectionGadgetsFix> fixes = new SmartList<>();
    fixes.add(new MakeClassFinalFix(callClass));
    if (!(method instanceof PsiCompiledElement)) {
      fixes.add(new MakeMethodFinalFix(method.getName()));
    }
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("overridable.method.call.in.constructor.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("overridable.method.call.in.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverridableMethodCallInConstructorVisitor();
  }

  private static class MakeMethodFinalFix extends InspectionGadgetsFix {

    private final String methodName;

    MakeMethodFinalFix(String methodName) {
      this.methodName = methodName;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("make.method.final.fix.name", methodName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Make method 'final'";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodName = descriptor.getPsiElement();
      final PsiElement methodExpression = methodName.getParent();
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)methodExpression.getParent();
      final PsiMethod method = methodCall.resolveMethod();
      if (method == null || !FileModificationService.getInstance().preparePsiElementsForWrite(method)) {
        return;
      }
      WriteAction.run(() -> method.getModifierList().setModifierProperty(PsiModifier.FINAL, true));
      if (method.getContainingFile() != methodExpression.getContainingFile()) {
        method.navigate(true);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  private static class OverridableMethodCallInConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isCallDuringObjectConstruction(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
        return;
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (containingClass == null || containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiMethod calledMethod = expression.resolveMethod();
      if (calledMethod == null || !PsiUtil.canBeOverridden(calledMethod) || calledMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}