/*
 * Copyright 2009-2013 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class AssertWithSideEffectsInspection extends BaseInspection {

  @NonNls
  private static final Set<String> resultSetSideEffectMethods =
    ContainerUtil.newHashSet("next", "first", "last", "absolute", "relative", "previous");

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assert.with.side.effects.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assert.with.side.effects.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertWithSideEffectsVisitor();
  }

  private static class AssertWithSideEffectsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      super.visitAssertStatement(statement);
      final PsiExpression condition = statement.getAssertCondition();
      if (condition == null) {
        return;
      }
      final SideEffectVisitor visitor = new SideEffectVisitor();
      condition.accept(visitor);
      if (!visitor.hasSideEffects()) {
        return;
      }
      registerStatementError(statement);
    }
  }

  private static class SideEffectVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean hasSideEffects;

    boolean hasSideEffects() {
      return hasSideEffects;
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      hasSideEffects = true;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (hasSideEffects) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (hasSideEffects) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      if (methodHasSideEffects(method)) {
        hasSideEffects = true;
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !"java.sql.ResultSet".equals(containingClass.getQualifiedName())) {
        return;
      }
      final String methodName = method.getName();
      if (resultSetSideEffectMethods.contains(methodName)) {
        hasSideEffects = true;
      }
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType)) {
        hasSideEffects = true;
      }
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType)) {
        hasSideEffects = true;
      }
    }
  }

  private static boolean methodHasSideEffects(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return false;
    }
    final MethodSideEffectVisitor visitor = new MethodSideEffectVisitor();
    body.accept(visitor);
    return visitor.hasSideEffects();
  }

  private static class MethodSideEffectVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean hasSideEffects;

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      if (hasSideEffects) {
        return;
      }
      checkExpression(expression.getLExpression());
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      if (hasSideEffects) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType)) {
        checkExpression(expression.getOperand());
      }
      super.visitPrefixExpression(expression);
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      if (hasSideEffects) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType)) {
        checkExpression(expression.getOperand());
      }
      super.visitPostfixExpression(expression);
    }

    private void checkExpression(PsiExpression operand) {
      if (!(operand instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)operand;
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiField) {
        hasSideEffects = true;
      }
    }

    private boolean hasSideEffects() {
      return hasSideEffects;
    }
  }
}