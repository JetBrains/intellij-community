/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.migration;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class EnumerationCanBeIterationInspectionBase extends BaseInspection {
  static final int KEEP_NOTHING = 0;
  static final int KEEP_INITIALIZATION = 1;
  static final int KEEP_DECLARATION = 2;
  @NonNls
  static final String ITERATOR_TEXT = "iterator()";
  @NonNls
  static final String KEY_SET_ITERATOR_TEXT = "keySet().iterator()";
  @NonNls
  static final String VALUES_ITERATOR_TEXT = "values().iterator()";

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "enumeration.can.be.iteration.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "enumeration.can.be.iteration.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EnumerationCanBeIterationVisitor();
  }

  private static class EnumerationCanBeIterationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      final boolean isElements;
      if ("elements".equals(methodName)) {
        isElements = true;
      }
      else if ("keys".equals(methodName)) {
        isElements = false;
      }
      else {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(expression,
                                                "java.util.Enumeration")) {
        return;
      }
      final PsiElement parent = expression.getParent();
      final PsiVariable variable;
      if (parent instanceof PsiLocalVariable) {
        variable = (PsiVariable)parent;
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)parent;
        final PsiExpression lhs = assignmentExpression.getLExpression();
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)lhs;
        final PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiVariable)) {
          return;
        }
        variable = (PsiVariable)element;
      }
      else {
        return;
      }
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(
        expression, PsiMethod.class);
      if (containingMethod == null) {
        return;
      }
      if (!isEnumerationMethodCalled(variable, containingMethod)) {
        return;
      }
      if (isElements) {
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (InheritanceUtil.isInheritor(containingClass,
                                        "java.util.Vector")) {
          registerMethodCallError(expression, ITERATOR_TEXT);
        }
        else if (InheritanceUtil.isInheritor(containingClass,
                                             "java.util.Hashtable")) {
          registerMethodCallError(expression, VALUES_ITERATOR_TEXT);
        }
      }
      else {
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (InheritanceUtil.isInheritor(containingClass,
                                        "java.util.Hashtable")) {
          registerMethodCallError(expression, KEY_SET_ITERATOR_TEXT);
        }
      }
    }

    private static boolean isEnumerationMethodCalled(
      @NotNull PsiVariable variable, @NotNull PsiElement context) {
      final EnumerationCanBeIterationVisitor.EnumerationMethodCalledVisitor visitor =
        new EnumerationCanBeIterationVisitor.EnumerationMethodCalledVisitor(variable);
      context.accept(visitor);
      return visitor.isEnumerationMethodCalled();
    }

    private static class EnumerationMethodCalledVisitor
      extends JavaRecursiveElementWalkingVisitor {

      private final PsiVariable variable;
      private boolean enumerationMethodCalled;

      private EnumerationMethodCalledVisitor(@NotNull PsiVariable variable) {
        this.variable = variable;
      }

      @Override
      public void visitMethodCallExpression(
        PsiMethodCallExpression expression) {
        if (enumerationMethodCalled) {
          return;
        }
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression =
          expression.getMethodExpression();
        @NonNls final String methodName =
          methodExpression.getReferenceName();
        if (!"hasMoreElements".equals(methodName) &&
            !"nextElement".equals(methodName)) {
          return;
        }
        final PsiExpression qualifierExpression =
          methodExpression.getQualifierExpression();
        if (!(qualifierExpression instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)qualifierExpression;
        final PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiVariable)) {
          return;
        }
        final PsiVariable variable = (PsiVariable)element;
        enumerationMethodCalled = this.variable.equals(variable);
      }

      private boolean isEnumerationMethodCalled() {
        return enumerationMethodCalled;
      }
    }
  }
}
