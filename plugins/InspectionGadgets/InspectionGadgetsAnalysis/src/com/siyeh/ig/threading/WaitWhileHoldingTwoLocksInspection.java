/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class WaitWhileHoldingTwoLocksInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "wait.while.holding.two.locks.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "wait.while.holding.two.locks.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WaitWhileHoldingTwoLocksVisitor();
  }

  private static class WaitWhileHoldingTwoLocksVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      checkErrorsIn(method);
    }

    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
      checkErrorsIn(initializer);
    }

    private void checkErrorsIn(PsiElement context) {
      context.accept(new JavaRecursiveElementWalkingVisitor() {
        private int m_numLocksHeld;

        @Override
        public void visitClass(PsiClass aClass) {
          // Do not recurse into
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (m_numLocksHeld < 2) {
            return;
          }
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
          final PsiParameterList parameterList =
            method.getParameterList();
          final int numParams = parameterList.getParametersCount();
          if (numParams > 2) {
            return;
          }
          final PsiParameter[] parameters = parameterList.getParameters();
          if (numParams > 0) {
            final PsiType parameterType = parameters[0].getType();
            if (!parameterType.equals(PsiType.LONG)) {
              return;
            }
          }
          if (numParams > 1) {
            final PsiType parameterType = parameters[1].getType();
            if (!parameterType.equals(PsiType.INT)) {
              return;
            }
          }
          registerMethodCallError(expression);
        }

        @Override
        public void visitMethod(@NotNull PsiMethod method) {
          if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            m_numLocksHeld++;
          }
          super.visitMethod(method);
        }

        @Override
        public void visitSynchronizedStatement(
          @NotNull PsiSynchronizedStatement synchronizedStatement) {
          m_numLocksHeld++;
          super.visitSynchronizedStatement(synchronizedStatement);
        }

        @Override
        protected void elementFinished(@NotNull PsiElement element) {
          super.elementFinished(element);
          if (element instanceof PsiMethod && ((PsiMethod)element).hasModifierProperty(PsiModifier.SYNCHRONIZED) || element instanceof PsiSynchronizedStatement) {
            m_numLocksHeld--;
          }
        }
      });
    }
  }
}