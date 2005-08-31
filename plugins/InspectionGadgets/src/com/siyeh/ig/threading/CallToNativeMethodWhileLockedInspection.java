/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class CallToNativeMethodWhileLockedInspection extends ExpressionInspection {

  public String getGroupDisplayName() {
    return GroupNames.THREADING_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new WaitNotInSynchronizedContextVisitor();
  }

  private static class WaitNotInSynchronizedContextVisitor extends BaseInspectionVisitor {
    private boolean m_inSynchronizedContext = false;

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!m_inSynchronizedContext) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.NATIVE)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if ("java.lang.Object".equals(className)) {
        return;
      }
      registerMethodCallError(expression);
    }

    public void visitMethod(@NotNull PsiMethod method) {
      final boolean wasInSynchronizedContext = m_inSynchronizedContext;
      if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {

        m_inSynchronizedContext = true;
      }
      super.visitMethod(method);
      if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {

        m_inSynchronizedContext = wasInSynchronizedContext;
      }
    }

    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement psiSynchronizedStatement) {
      final boolean wasInSynchronizedContext = m_inSynchronizedContext;
      m_inSynchronizedContext = true;
      super.visitSynchronizedStatement(psiSynchronizedStatement);
      m_inSynchronizedContext = wasInSynchronizedContext;
    }
  }
}
