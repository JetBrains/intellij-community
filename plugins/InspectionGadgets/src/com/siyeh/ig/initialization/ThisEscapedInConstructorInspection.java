/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class ThisEscapedInConstructorInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "ThisEscapedInObjectConstruction";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("this.reference.escaped.in.construction.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("this.reference.escaped.in.construction.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThisExposedInConstructorInspectionVisitor();
  }

  private static class ThisExposedInConstructorInspectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThisExpression(PsiThisExpression expression) {
      super.visitThisExpression(expression);
      if (!isInInitializer(expression)) {
        return;
      }
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      final PsiClass containingClass = ClassUtils.getContainingClass(expression);
      if (qualifier != null) {
        final PsiElement element = qualifier.resolve();
        if (!(element instanceof PsiClass)) {
          return;
        }
        final PsiClass aClass = (PsiClass)element;
        if (!aClass.equals(containingClass)) {
          return;
        }
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
        if (!thisEscapesToField(expression, assignmentExpression)) {
          return;
        }
        registerError(expression);
      }
      else if (parent instanceof PsiExpressionList) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiNewExpression) {
          final PsiNewExpression newExpression = (PsiNewExpression)grandParent;
          if (!thisEscapesToConstructor(expression, newExpression)) {
            return;
          }
          registerError(expression);
        }
        else if (grandParent instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
          if (!thisEscapesToMethod(expression, methodCallExpression)) {
            return;
          }
          registerError(expression);
        }
      }
    }

    private static boolean thisEscapesToMethod(PsiThisExpression expression, PsiMethodCallExpression methodCallExpression) {
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return false;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
      return methodClass != null && !containingClass.isInheritor(methodClass, true);
    }

    private static boolean thisEscapesToConstructor(PsiThisExpression expression, PsiNewExpression newExpression) {
      final PsiClass containingClass = ClassUtils.getContainingClass(expression);
      final PsiJavaCodeReferenceElement referenceElement = newExpression.getClassReference();
      if (referenceElement == null) {
        return false;
      }
      final PsiElement element = referenceElement.resolve();
      if (!(element instanceof PsiClass)) {
        return false;
      }
      final PsiClass constructorClass = (PsiClass)element;
      return !PsiTreeUtil.isAncestor(containingClass, constructorClass, false) ||
             constructorClass.hasModifierProperty(PsiModifier.STATIC);
    }

    private static boolean thisEscapesToField(PsiThisExpression expression, PsiAssignmentExpression assignmentExpression) {
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (!expression.equals(rhs)) {
        return false;
      }
      final PsiExpression lExpression = assignmentExpression.getLExpression();
      if (!(lExpression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression leftExpression = (PsiReferenceExpression)lExpression;
      final PsiElement element = leftExpression.resolve();
      if (!(element instanceof PsiField)) {
        return false;
      }
      final PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
      final PsiClass assignmentClass = ClassUtils.getContainingClass(assignmentExpression);
      final PsiClass fieldClass = field.getContainingClass();
      return !(assignmentClass == null || fieldClass == null || assignmentClass.isInheritor(fieldClass, true) ||
               PsiTreeUtil.isAncestor(assignmentClass, fieldClass, false));
    }

    /**
     * @return true if CallExpression is in a constructor, instance
     *         initializer, or field initializer. Otherwise false
     */
    private static boolean isInInitializer(PsiElement call) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class, true, PsiClass.class);
      if (method != null) {
        return method.isConstructor();
      }
      final PsiField field = PsiTreeUtil.getParentOfType(call, PsiField.class, true, PsiClass.class);
      if (field != null) {
        return true;
      }
      final PsiClassInitializer classInitializer = PsiTreeUtil.getParentOfType(call, PsiClassInitializer.class, true, PsiClass.class);
      return classInitializer != null && !classInitializer.hasModifierProperty(PsiModifier.STATIC);
    }
  }
}