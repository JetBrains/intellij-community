/*
 * Copyright 2011-2018 Bas Leijdekkers
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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AmbiguousFieldAccessInspection extends BaseInspection {

  private static final Logger LOGGER = Logger.getInstance(AmbiguousFieldAccessInspection.class);

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new AmbiguousFieldAccessVisitor();
  }

  private static class AmbiguousFieldAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
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
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
      final String referenceText = expression.getText();
      final PsiVariable variable = resolveHelper.resolveAccessibleReferencedVariable(referenceText, parent);
      if (variable == null || field == variable) {
        return;
      }
      final PsiElement commonParent = PsiTreeUtil.findCommonParent(variable, containingClass);
      if (commonParent == null) {
        return;
      }
      registerError(expression, fieldClass, variable);
    }
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new AmbiguousFieldAccessFix();
  }

  private static class AmbiguousFieldAccessFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("ambiguous.field.access.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      final String newExpressionText = "super." + referenceExpression.getText();
      PsiReplacementUtil.replaceExpression(referenceExpression, newExpressionText);
    }

  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiClass fieldClass = (PsiClass)infos[0];
    final PsiVariable variable = (PsiVariable)infos[1];
    if (variable instanceof PsiLocalVariable) {
      final PsiLocalVariable localVariable = (PsiLocalVariable)variable;
      return InspectionGadgetsBundle.message("ambiguous.field.access.hides.local.variable.problem.descriptor",
                                             fieldClass.getName(),
                                             localVariable.getName());
    }
    else if (variable instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)variable;
      return InspectionGadgetsBundle.message("ambiguous.field.access.hides.parameter.problem.descriptor",
                                             fieldClass.getName(),
                                             parameter.getName(),
                                             getMethodName(parameter));
    }
    else if (variable instanceof PsiField) {
      final PsiField actualField = (PsiField)variable;
      return InspectionGadgetsBundle.message("ambiguous.field.access.hides.field.problem.descriptor",
                                             fieldClass.getName(),
                                             getFqFieldName(actualField));
    }
    LOGGER.error("Variable of " + variable.getClass() + " is neither PsiLocalVariable, nor PsiParameter, nor PsiField");
    return "";
  }

  private static @NotNull String getFqFieldName(@NotNull PsiField field) {
    final String fieldName = field.getName();

    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return fieldName;

    final String classFqName = containingClass.getQualifiedName();
    if (classFqName == null) return fieldName;

    return Objects.requireNonNullElse(classFqName, "") + "#" + fieldName;
  }

  private static @NotNull String getMethodName(PsiParameter parameter) {
    final PsiMethod method = ObjectUtils.tryCast(parameter.getDeclarationScope(), PsiMethod.class);
    if (method == null) return "";

    final String methodName = method.getName();

    final PsiClass enclosingClass = method.getContainingClass();
    if (enclosingClass == null) return methodName;

    final String classFqName = enclosingClass.getQualifiedName();
    if (classFqName == null) return methodName;

    return classFqName + "#" + methodName;
  }
}