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

public class PublicFieldAccessedInSynchronizedContextInspection extends ExpressionInspection {

  public String getID() {
    return "NonPrivateFieldAccessedInSynchronizedContext";
  }

  public String getGroupDisplayName() {
    return GroupNames.THREADING_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new PublicFieldAccessedInSynchronizedContextVisitor();
  }

  private static class PublicFieldAccessedInSynchronizedContextVisitor
    extends BaseInspectionVisitor {
    private boolean m_inSynchronizedContext = false;

    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (!m_inSynchronizedContext) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.PRIVATE) ||
          field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      registerError(expression);
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
