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
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class StaticCallOnSubclassInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getID() {
    return "StaticMethodReferencedViaSubclass";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "static.method.via.subclass.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass declaringClass = (PsiClass)infos[0];
    final PsiClass referencedClass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message(
      "static.method.via.subclass.problem.descriptor",
      declaringClass.getQualifiedName(), referencedClass.getQualifiedName());
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new StaticCallOnSubclassFix();
  }

  private static class StaticCallOnSubclassFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("static.method.via.subclass.rationalize.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiIdentifier name = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiIdentifier.class);
      if (name == null) return;
      final PsiReferenceExpression expression = ObjectUtils.tryCast(name.getParent(), PsiReferenceExpression.class);
      if (expression == null) {
        return;
      }
      if (expression instanceof PsiMethodReferenceExpression) {
        PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiMethod) {
          PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
          if (containingClass != null) {
            PsiExpression qualifierExpression = expression.getQualifierExpression();
            if (qualifierExpression instanceof PsiReferenceExpression) {
              ((PsiReferenceExpression)qualifierExpression).bindToElement(containingClass);
            }
          }
        }
        return;
      }
      final PsiMethodCallExpression call = ObjectUtils.tryCast(expression.getParent(), PsiMethodCallExpression.class);
      if (call == null) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      final PsiExpressionList argumentList = call.getArgumentList();
      if (containingClass == null) {
        return;
      }
      final String containingClassName = containingClass.getQualifiedName();
      CommentTracker commentTracker = new CommentTracker();
      final String argText = commentTracker.text(argumentList);
      final String typeArgText = commentTracker.text(call.getTypeArgumentList());
      final String methodName = expression.getReferenceName();
      PsiReplacementUtil.replaceExpressionAndShorten(call, containingClassName + '.' + typeArgText + methodName + argText, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticCallOnSubclassVisitor();
  }

  private static class StaticCallOnSubclassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
       final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
     
      checkCallOnSubclass(method, methodExpression);
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (PsiMethodReferenceUtil.isStaticallyReferenced(expression)) {
        PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiMethod) {
          checkCallOnSubclass((PsiMethod)resolve, expression);
        }
      }
    }

    private void checkCallOnSubclass(@NotNull PsiMethod method, PsiReferenceExpression methodExpression) {
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)qualifier).resolve();
      if (!(referent instanceof PsiClass)) {
        return;
      }
      final PsiClass referencedClass = (PsiClass)referent;
      final PsiClass declaringClass = method.getContainingClass();
      if (declaringClass == null) {
        return;
      }
      if (declaringClass.equals(referencedClass) || declaringClass.isInterface()) {
        return;
      }
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(methodExpression.getProject()).getResolveHelper();
      if (!resolveHelper.isAccessible(declaringClass, methodExpression, null)) {
        return;
      }
      PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
      if (referenceNameElement == null) {
        return;
      }
      registerError(referenceNameElement, declaringClass, referencedClass);
    }
  }
}