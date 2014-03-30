/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WaitNotInSynchronizedContextInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "WaitWhileNotSynced";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "wait.not.in.synchronized.context.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    @NonNls final String text;
    if (infos.length > 0) {
      final PsiElement element = (PsiElement)infos[0];
      text = element.getText();
    }
    else {
      text = PsiKeyword.THIS;
    }
    return InspectionGadgetsBundle.message(
      "wait.not.in.synchronized.context.problem.descriptor", text);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WaitNotInSynchronizedContextVisitor();
  }

  private static class WaitNotInSynchronizedContextVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.WAIT.equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String qualifiedName = aClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null ||
          qualifier instanceof PsiThisExpression ||
          qualifier instanceof PsiSuperExpression) {
        if (isSynchronizedOnThis(expression)) {
          return;
        }
        registerError(expression);
      }
      else if (qualifier instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)qualifier;
        final PsiElement target = referenceExpression.resolve();
        if (isSynchronizedOn(expression, target)) {
          return;
        }
        registerError(expression, qualifier);
      }
    }

    private static boolean isSynchronizedOn(@NotNull PsiElement element,
                                            @Nullable PsiElement target) {
      if (target == null) {
        return false;
      }
      final PsiElement context =
        PsiTreeUtil.getParentOfType(element,
                                    PsiSynchronizedStatement.class);
      if (context == null) {
        return false;
      }
      final PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)context;
      final PsiExpression lockExpression =
        synchronizedStatement.getLockExpression();
      if (!(lockExpression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)lockExpression;
      final PsiElement lockTarget = referenceExpression.resolve();
      return target.equals(lockTarget) ||
             isSynchronizedOn(synchronizedStatement, target);
    }

    private static boolean isSynchronizedOnThis(
      @NotNull PsiElement element) {
      final PsiElement context =
        PsiTreeUtil.getParentOfType(element, PsiMethod.class,
                                    PsiSynchronizedStatement.class);
      if (context instanceof PsiSynchronizedStatement) {
        final PsiSynchronizedStatement synchronizedStatement =
          (PsiSynchronizedStatement)context;
        final PsiExpression lockExpression =
          synchronizedStatement.getLockExpression();
        return lockExpression instanceof PsiThisExpression ||
               isSynchronizedOnThis(synchronizedStatement);
      }
      else if (context instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)context;
        if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          return true;
        }
      }
      return false;
    }
  }
}