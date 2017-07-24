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
package com.siyeh.ig.cloneable;

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UseOfCloneInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("use.of.clone.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Object errorElement = infos[0];
    if (errorElement instanceof PsiMethodCallExpression) {
      return InspectionGadgetsBundle.message("use.of.clone.call.problem.descriptor");
    }
    else if (errorElement instanceof PsiMethod) {
      return InspectionGadgetsBundle.message("use.of.clone.call.method.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("use.of.clone.reference.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfCloneVisitor();
  }

  private static class UseOfCloneVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.CLONE.equals(referenceName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        return;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression != null) {
        final PsiType type = qualifierExpression.getType();
        if (type instanceof PsiArrayType) {
          return;
        }
      }
      registerMethodCallError(expression, expression);
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiMethod) || !CloneUtils.isClone((PsiMethod)target)) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      final String qualifiedName = reference.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_CLONEABLE.equals(qualifiedName)) {
        return;
      }
      registerError(reference, reference);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      if (!CloneUtils.isClone(method) || ControlFlowUtils.methodAlwaysThrowsException(method)) {
        return;
      }
      registerMethodError(method, method);
    }
  }
}
