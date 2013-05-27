/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InitializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryDefaultInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.default.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.default.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryDefaultVisitor();
  }

  private static class UnnecessaryDefaultVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(
      @NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      final PsiSwitchLabelStatement defaultStatement =
        retrieveUnnecessaryDefault(statement);
      if (defaultStatement == null) {
        return;
      }
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(
        defaultStatement, PsiStatement.class);
      while (nextStatement != null &&
             !(nextStatement instanceof PsiBreakStatement) &&
             !(nextStatement instanceof PsiSwitchLabelStatement)) {
        if (nextStatement instanceof PsiThrowStatement ||
            isStatementNeededForInitializationOfVariable(statement,
                                                         nextStatement)) {
          return;
        }
        nextStatement = PsiTreeUtil.getNextSiblingOfType(
          nextStatement, PsiStatement.class);
      }
      registerStatementError(defaultStatement);
    }

    private static boolean isStatementNeededForInitializationOfVariable(
      PsiSwitchStatement switchStatement, PsiStatement statement) {
      if (!(statement instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)statement;
      final PsiExpression expression =
        expressionStatement.getExpression();
      if (!(expression instanceof PsiAssignmentExpression)) {
        return false;
      }
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)expression;
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiLocalVariable)) {
        return false;
      }
      final PsiLocalVariable variable = (PsiLocalVariable)target;
      return InitializationUtils.switchStatementAssignsVariableOrFails(
        switchStatement, variable, true);
    }

    @Nullable
    private static PsiSwitchLabelStatement retrieveUnnecessaryDefault(
      PsiSwitchStatement statement) {
      final PsiExpression expression = statement.getExpression();
      if (expression == null) {
        return null;
      }
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return null;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null || !aClass.isEnum()) {
        return null;
      }
      final PsiCodeBlock body = statement.getBody();
      if (body == null) {
        return null;
      }
      final PsiStatement[] statements = body.getStatements();
      int numCases = 0;
      PsiSwitchLabelStatement result = null;
      for (final PsiStatement child : statements) {
        if (!(child instanceof PsiSwitchLabelStatement)) {
          continue;
        }
        final PsiSwitchLabelStatement labelStatement =
          (PsiSwitchLabelStatement)child;
        if (labelStatement.isDefaultCase()) {
          result = labelStatement;
        }
        else {
          numCases++;
        }
      }
      if (result == null) {
        return null;
      }
      final PsiField[] fields = aClass.getFields();
      int numEnums = 0;
      for (final PsiField field : fields) {
        final PsiType fieldType = field.getType();
        if (fieldType.equals(type)) {
          numEnums++;
        }
      }
      if (numEnums != numCases) {
        return null;
      }
      return result;
    }
  }
}