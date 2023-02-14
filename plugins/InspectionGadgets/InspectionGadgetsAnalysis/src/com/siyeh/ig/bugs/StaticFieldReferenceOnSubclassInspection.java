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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NotNull;

public class StaticFieldReferenceOnSubclassInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getID() {
    return "StaticFieldReferencedViaSubclass";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass declaringClass = (PsiClass)infos[0];
    final PsiClass referencedClass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message(
      "static.field.via.subclass.problem.descriptor",
      declaringClass.getQualifiedName(),
      referencedClass.getQualifiedName());
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new StaticFieldOnSubclassFix();
  }

  private static class StaticFieldOnSubclassFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "static.field.via.subclass.rationalize.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiIdentifier name = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiIdentifier.class);
      if (name == null) return;
      final PsiReferenceExpression expression = ObjectUtils.tryCast(name.getParent(), PsiReferenceExpression.class);
      if (expression == null) return;
      final PsiField field = ObjectUtils.tryCast(expression.resolve(), PsiField.class);
      if (field == null) return;
      PsiReplacementUtil.replaceExpressionWithReferenceTo(expression, field);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticFieldOnSubclassVisitor();
  }

  private static class StaticFieldOnSubclassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement qualifier = expression.getQualifier();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiField field)) {
        return;
      }
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiElement qualifierReferent = ((PsiReferenceExpression)qualifier).resolve();
      if (!(qualifierReferent instanceof PsiClass referencedClass)) {
        return;
      }
      final PsiClass declaringClass = field.getContainingClass();
      if (declaringClass == null || declaringClass.equals(referencedClass)) {
        return;
      }
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
      if (!resolveHelper.isAccessible(declaringClass, expression, null)) {
        return;
      }
      final PsiElement identifier = expression.getReferenceNameElement();
      if (identifier == null) {
        return;
      }
      registerError(identifier, declaringClass, referencedClass);
    }
  }
}