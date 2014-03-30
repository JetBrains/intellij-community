/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SynchronizationUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NonAtomicOperationOnVolatileFieldInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "non.atomic.operation.on.volatile.field.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "non.atomic.operation.on.volatile.field.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonAtomicOperationOnVolatileFieldVisitor();
  }

  private static class NonAtomicOperationOnVolatileFieldVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(
      @NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression rhs = expression.getRExpression();
      if (rhs == null) {
        return;
      }
      final PsiExpression lhs = expression.getLExpression();
      final PsiField volatileField = findNonSynchronizedVolatileField(lhs);
      if (volatileField == null) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSEQ) ||
          tokenType.equals(JavaTokenType.MINUSEQ) ||
          tokenType.equals(JavaTokenType.ASTERISKEQ) ||
          tokenType.equals(JavaTokenType.DIVEQ) ||
          tokenType.equals(JavaTokenType.ANDEQ) ||
          tokenType.equals(JavaTokenType.OREQ) ||
          tokenType.equals(JavaTokenType.XOREQ) ||
          tokenType.equals(JavaTokenType.PERCEQ) ||
          tokenType.equals(JavaTokenType.LTLTEQ) ||
          tokenType.equals(JavaTokenType.GTGTEQ) ||
          tokenType.equals(JavaTokenType.GTGTGTEQ)) {
        registerError(lhs);
        return;
      }
      if (VariableAccessUtils.variableIsUsed(volatileField, rhs)) {
        registerError(lhs);
      }
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUS.equals(tokenType) ||
          JavaTokenType.MINUS.equals(tokenType) ||
          JavaTokenType.EXCL.equals(tokenType)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiField volatileField =
        findNonSynchronizedVolatileField(operand);
      if (volatileField == null) {
        return;
      }
      registerError(operand);
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      super.visitPostfixExpression(expression);
      final PsiExpression operand = expression.getOperand();
      final PsiField volatileField =
        findNonSynchronizedVolatileField(operand);
      if (volatileField == null) {
        return;
      }
      registerError(operand);
    }

    @Nullable
    private static PsiField findNonSynchronizedVolatileField(
      PsiExpression expression) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression reference =
        (PsiReferenceExpression)expression;
      if (SynchronizationUtil.isInSynchronizedContext(reference)) {
        return null;
      }
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiField)) {
        return null;
      }
      final PsiField field = (PsiField)referent;
      if (!field.hasModifierProperty(PsiModifier.VOLATILE)) {
        return null;
      }
      return field;
    }
  }
}