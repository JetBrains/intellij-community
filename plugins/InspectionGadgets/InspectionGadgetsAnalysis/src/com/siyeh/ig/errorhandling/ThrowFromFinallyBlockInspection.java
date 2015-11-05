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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class ThrowFromFinallyBlockInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "throw.from.finally.block.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "throw.from.finally.block.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowFromFinallyBlockVisitor();
  }

  private static class ThrowFromFinallyBlockVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiCodeBlock finallyBlock = getContainingFinallyBlock(statement);
      if (finallyBlock == null) {
        return;
      }
      final PsiExpression exception = ParenthesesUtils.stripParentheses(statement.getException());
      if (exception instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)exception;
        final PsiElement target = referenceExpression.resolve();
        if (target == null || !PsiTreeUtil.isAncestor(finallyBlock, target, true)) {
          // variable from outside finally block is thrown
          return;
        }
      }
      registerStatementError(statement);
    }
  }

  private static PsiCodeBlock getContainingFinallyBlock(@NotNull PsiThrowStatement throwStatement) {
    final PsiExpression exception = throwStatement.getException();
    final PsiType type = exception != null ? exception.getType() : null;
    PsiElement currentElement = throwStatement;
    while (true) {
      final PsiTryStatement tryStatement = PsiTreeUtil
        .getParentOfType(currentElement, PsiTryStatement.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (tryStatement == null) {
        return null;
      }
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (PsiTreeUtil.isAncestor(finallyBlock, currentElement, true)) {
        return finallyBlock;
      }
      if (type != null && isCaught(tryStatement, type)) {
        return null;
      }
      currentElement = tryStatement;
    }
  }

  private static boolean isCaught(PsiTryStatement tryStatement, PsiType exceptionType) {
    for (PsiParameter parameter : tryStatement.getCatchBlockParameters()) {
      final PsiType type = parameter.getType();
      if (type.isAssignableFrom(exceptionType)) return true;
    }
    return false;
  }
}