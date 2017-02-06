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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class ForLoopThatDoesntUseLoopVariableInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("for.loop.not.use.loop.variable.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean condition = ((Boolean)infos[0]).booleanValue();
    final boolean update = ((Boolean)infos[1]).booleanValue();
    if (condition && update) {
      return InspectionGadgetsBundle.message(
        "for.loop.not.use.loop.variable.problem.descriptor.both.condition.and.update");
    }
    if (condition) {
      return InspectionGadgetsBundle.message(
        "for.loop.not.use.loop.variable.problem.descriptor.condition");
    }
    return InspectionGadgetsBundle.message(
      "for.loop.not.use.loop.variable.problem.descriptor.update");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ForLoopThatDoesntUseLoopVariableVisitor();
  }

  private static class ForLoopThatDoesntUseLoopVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      PsiLocalVariable variable = extractInitializerVariable(statement);
      if (variable == null) return;
      boolean notUsedInCondition = !conditionUsesVariable(statement, variable);
      boolean notUsedInUpdate = !updateUsesVariable(statement, variable);
      if (notUsedInCondition || notUsedInUpdate) {
        if (!notUsedInCondition && isDeclarationUsedAsBound(statement, variable)) return;
        registerStatementError(statement, notUsedInCondition, notUsedInUpdate);
      }
    }

    private static boolean isDeclarationUsedAsBound(PsiForStatement statement, PsiLocalVariable boundVar) {
      PsiBinaryExpression condition = tryCast(PsiUtil.skipParenthesizedExprDown(statement.getCondition()), PsiBinaryExpression.class);
      if (condition == null || !ComparisonUtils.isComparisonOperation(condition.getOperationTokenType())) return false;
      PsiExpression otherOperand = null;
      if (ExpressionUtils.isReferenceTo(condition.getLOperand(), boundVar)) {
        otherOperand = condition.getROperand();
      } else if (ExpressionUtils.isReferenceTo(condition.getROperand(), boundVar)) {
        otherOperand = condition.getLOperand();
      }
      if (otherOperand == null) return false;
      PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(otherOperand), PsiReferenceExpression.class);
      if (ref == null) return false;
      PsiVariable indexVar = tryCast(ref.resolve(), PsiVariable.class);
      if (indexVar == null) return false;
      PsiStatement update = statement.getUpdate();
      return VariableAccessUtils.variableIsIncremented(indexVar, update) || VariableAccessUtils.variableIsDecremented(indexVar, update);
    }

    private static PsiLocalVariable extractInitializerVariable(PsiForStatement statement) {
      final PsiDeclarationStatement declaration = tryCast(statement.getInitialization(), PsiDeclarationStatement.class);
      if (declaration == null) return null;

      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length != 1) return null;

      return tryCast(declaredElements[0], PsiLocalVariable.class);
    }

    private static boolean conditionUsesVariable(PsiForStatement statement, PsiLocalVariable variable) {
      final PsiExpression condition = statement.getCondition();
      return condition == null || VariableAccessUtils.variableIsUsed(variable, condition);
    }

    private static boolean updateUsesVariable(PsiForStatement statement, PsiLocalVariable variable) {
      final PsiStatement update = statement.getUpdate();
      return update == null || VariableAccessUtils.variableIsUsed(variable, update);
    }
  }
}