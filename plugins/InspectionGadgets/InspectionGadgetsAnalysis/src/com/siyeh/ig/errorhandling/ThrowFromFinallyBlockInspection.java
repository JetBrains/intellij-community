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

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

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
    if (infos.length == 0) {
      return InspectionGadgetsBundle.message("throw.from.finally.block.problem.descriptor");
    }
    else {
      final PsiClassType type = (PsiClassType)infos[0];
      return InspectionGadgetsBundle.message("possible.throw.from.finally.block.problem.descriptor", type.getPresentableText());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowFromFinallyBlockVisitor();
  }

  private static class ThrowFromFinallyBlockVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final List<PsiClassType> exceptions = ExceptionUtil.getThrownExceptions(expression);
      if (exceptions.isEmpty()) {
        return;
      }
      for (PsiClassType exception : exceptions) {
        final PsiCodeBlock finallyBlock = getContainingFinallyBlock(expression, exception);
        if (finallyBlock != null && isHidingOfPreviousException(finallyBlock, expression)) {
          registerMethodCallError(expression, exception);
          return;
        }
      }
    }

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception = ParenthesesUtils.stripParentheses(statement.getException());
      if (exception == null) {
        return;
      }
      final PsiType type = exception.getType();
      if (type == null) {
        return;
      }
      final PsiCodeBlock finallyBlock = getContainingFinallyBlock(statement, type);
      if (finallyBlock == null) {
        return;
      }
      if (exception instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)exception;
        final PsiElement target = referenceExpression.resolve();
        if (target == null || !PsiTreeUtil.isAncestor(finallyBlock, target, true)) {
          // variable from outside finally block is thrown
          return;
        }
      }
      if (isHidingOfPreviousException(finallyBlock, statement)) {
        registerStatementError(statement);
      }
    }

    private static boolean isHidingOfPreviousException(PsiCodeBlock finallyBlock, PsiElement throwElement) {
      final PsiElement parent = finallyBlock.getParent();
      if (!(parent instanceof PsiTryStatement)) {
        // never reached
        return false;
      }
      final PsiTryStatement tryStatement = (PsiTryStatement)parent;
      final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
      if (catchBlocks.length == 0) {
        return true;
      }
      final PsiIfStatement ifStatement = getParentOfType(throwElement, PsiIfStatement.class, finallyBlock);
      if (ifStatement == null) {
        return true;
      }
      final boolean inThenBranch = PsiTreeUtil.isAncestor(ifStatement.getThenBranch(), throwElement, false);
      final boolean inElseBranch = PsiTreeUtil.isAncestor(ifStatement.getElseBranch(), throwElement, false);
      if (!inThenBranch && !inElseBranch) {
        return true;
      }
      final PsiExpression condition = ifStatement.getCondition();
      final PsiVariable variable = ExpressionUtils.getVariableFromNullComparison(condition, inThenBranch);
      if (variable == null) {
        return true;
      }
      boolean assigned = true;
      for (PsiCodeBlock catchBlock : catchBlocks) {
        assigned &= VariableAccessUtils.variableIsAssigned(variable, catchBlock);
      }
      return !assigned;
    }

    @Nullable
    public static <T extends PsiElement> T getParentOfType(@Nullable PsiElement element,
                                                           @NotNull Class<T> aClass,
                                                           @NotNull PsiElement stopAt) {
      if (element == null || element instanceof PsiFile) return null;
      element = element.getParent();

      while (element != null && !aClass.isInstance(element)) {
        if (element == stopAt || element instanceof PsiFile) return null;
        element = element.getParent();
      }
      //noinspection unchecked
      return (T)element;
    }
  }

  private static PsiCodeBlock getContainingFinallyBlock(@NotNull PsiElement element, @NotNull PsiType thrownType) {
    PsiElement currentElement = element;
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
      if (PsiTreeUtil.isAncestor(tryStatement.getTryBlock(), currentElement, true) && isCaught(tryStatement, thrownType)) {
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