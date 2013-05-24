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

public class UnconditionalWaitInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unconditional.wait.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unconditional.wait.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnconditionalWaitVisitor();
  }

  private static class UnconditionalWaitVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body != null) {
        checkBody(body);
      }
    }

    @Override
    public void visitSynchronizedStatement(
      @NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiCodeBlock body = statement.getBody();
      if (body != null) {
        checkBody(body);
      }
    }

    private void checkBody(PsiCodeBlock body) {
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return;
      }
      for (final PsiStatement statement : statements) {
        if (isConditional(statement)) {
          return;
        }
        if (!(statement instanceof PsiExpressionStatement)) {
          continue;
        }
        final PsiExpression firstExpression =
          ((PsiExpressionStatement)statement).getExpression();
        if (!(firstExpression instanceof PsiMethodCallExpression)) {
          continue;
        }
        final PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)firstExpression;
        final PsiReferenceExpression methodExpression =
          methodCallExpression.getMethodExpression();
        @NonNls final String methodName =
          methodExpression.getReferenceName();
        if (!HardcodedMethodConstants.WAIT.equals(methodName)) {
          continue;
        }
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
          continue;
        }
        final PsiParameterList parameterList =
          method.getParameterList();
        final int numParams = parameterList.getParametersCount();
        if (numParams > 2) {
          continue;
        }
        final PsiParameter[] parameters = parameterList.getParameters();
        if (numParams > 0) {
          final PsiType parameterType = parameters[0].getType();
          if (!parameterType.equals(PsiType.LONG)) {
            continue;
          }
        }
        if (numParams > 1) {
          final PsiType parameterType = parameters[1].getType();
          if (!parameterType.equals(PsiType.INT)) {
            continue;
          }
        }
        registerMethodCallError(methodCallExpression);
      }
    }

    private static boolean isConditional(PsiStatement statement) {
      return statement instanceof PsiIfStatement;
    }
  }
}