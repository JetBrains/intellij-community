/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.initialization;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class NonThreadSafeLazyInitializationInspectionBase extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "non.thread.safe.lazy.initialization.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "non.thread.safe.lazy.initialization.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnsafeSafeLazyInitializationVisitor();
  }

  private static class UnsafeSafeLazyInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(
      @NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression lhs = expression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression reference = (PsiReferenceExpression)lhs;
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (isInStaticInitializer(expression)) {
        return;
      }
      if (isInSynchronizedContext(expression)) {
        return;
      }
      final PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
      final PsiElement parent =
        PsiTreeUtil.skipParentsOfType(statement, PsiCodeBlock.class, PsiBlockStatement.class);
      if (!(parent instanceof PsiIfStatement)) {
        return;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)parent;
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null|| !isNullComparison(condition, field)) {
        return;
      }
      registerError(lhs, ifStatement, field);
    }

    private static boolean isNullComparison(PsiExpression condition, PsiVariable variable) {
      if (!(condition instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression comparison = (PsiBinaryExpression)condition;
      final IElementType tokenType = comparison.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQEQ)) {
        return false;
      }
      final PsiExpression lhs = comparison.getLOperand();
      final PsiExpression rhs = comparison.getROperand();
      if (ExpressionUtils.isNullLiteral(rhs)) {
        return VariableAccessUtils.evaluatesToVariable(lhs, variable);
      }
      else if (ExpressionUtils.isNullLiteral(lhs)) {
        return VariableAccessUtils.evaluatesToVariable(rhs, variable);
      }
      return false;
    }

    private static boolean isInSynchronizedContext(PsiElement element) {
      final PsiSynchronizedStatement syncBlock =
        PsiTreeUtil.getParentOfType(element,
                                    PsiSynchronizedStatement.class);
      if (syncBlock != null) {
        return true;
      }
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(element,
                                    PsiMethod.class);
      return method != null &&
             method.hasModifierProperty(PsiModifier.SYNCHRONIZED)
             && method.hasModifierProperty(PsiModifier.STATIC);
    }

    private static boolean isInStaticInitializer(PsiElement element) {
      final PsiClassInitializer initializer =
        PsiTreeUtil.getParentOfType(element,
                                    PsiClassInitializer.class);
      return initializer != null &&
             initializer.hasModifierProperty(PsiModifier.STATIC);
    }
  }
}