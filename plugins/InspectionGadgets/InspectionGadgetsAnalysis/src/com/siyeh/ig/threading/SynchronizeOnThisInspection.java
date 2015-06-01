/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class SynchronizeOnThisInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "synchronize.on.this.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "synchronize.on.this.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnThisVisitor();
  }

  private static class SynchronizeOnThisVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(
      @NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = statement.getLockExpression();
      if (!(lockExpression instanceof PsiThisExpression)) {
        return;
      }
      registerError(lockExpression);
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier != null &&
          !(qualifier instanceof PsiThisExpression)) {
        return;
      }
      if (!ThreadingUtils.isNotifyOrNotifyAllCall(expression) && !ThreadingUtils.isWaitCall(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}