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

public class NakedNotifyInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("naked.notify.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "naked.notify.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NakedNotifyVisitor();
  }

  private static class NakedNotifyVisitor extends BaseInspectionVisitor {

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
      final PsiStatement firstStatement = statements[0];
      if (!(firstStatement instanceof PsiExpressionStatement)) {
        return;
      }
      final PsiExpression firstExpression =
        ((PsiExpressionStatement)firstStatement).getExpression();
      if (!(firstExpression instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)firstExpression;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.NOTIFY.equals(methodName) &&
          !HardcodedMethodConstants.NOTIFY_ALL.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      if (argumentList.getExpressions().length != 0) {
        return;
      }
      registerMethodCallError(methodCallExpression);
    }
  }
}