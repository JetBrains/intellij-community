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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class NestedSynchronizedStatementInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "nested.synchronized.statement.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "nested.synchronized.statement.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NestedSynchronizedStatementVisitor();
  }

  private static class NestedSynchronizedStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      if (isNestedStatement(statement, PsiSynchronizedStatement.class)) {
        registerStatementError(statement);
      }
    }
  }

  public static <T extends PsiStatement> boolean isNestedStatement(@NotNull PsiStatement statement, Class<T> aClass) {
    final PsiElement containingStatement = PsiTreeUtil.getParentOfType(statement, aClass);
    if (containingStatement == null) {
      return false;
    }
    final NavigatablePsiElement containingElement = PsiTreeUtil.getParentOfType(statement, PsiMember.class, true, PsiLambdaExpression.class);
    final NavigatablePsiElement containingContainingElement = PsiTreeUtil.getParentOfType(containingStatement, PsiMember.class, true, PsiLambdaExpression.class);
    if (containingElement == null ||
        containingContainingElement == null ||
        !containingElement.equals(containingContainingElement)) {
      return false;
    }
    return true;
  }
}