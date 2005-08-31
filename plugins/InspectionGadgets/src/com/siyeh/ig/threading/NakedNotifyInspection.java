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
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class NakedNotifyInspection extends MethodInspection {

  public String getGroupDisplayName() {
    return GroupNames.THREADING_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NakedNotifyVisitor();
  }

  private static class NakedNotifyVisitor extends BaseInspectionVisitor {

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

    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
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
      if (methodExpression == null) {
        return;
      }
      @NonNls final String methodName = methodExpression.getReferenceName();

      if (!"notify".equals(methodName) && !"notifyAll".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      if (argumentList.getExpressions().length != 0) {
        return;
      }
      registerMethodCallError(methodCallExpression);
    }
  }
}
