// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      if (!HardcodedMethodConstants.CLONE.equals(referenceName) || !expression.getArgumentList().isEmpty()) {
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
