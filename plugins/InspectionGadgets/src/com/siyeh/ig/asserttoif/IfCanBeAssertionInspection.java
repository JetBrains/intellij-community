/*
 * Copyright 2010-2017 Bas Leijdekkers
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
package com.siyeh.ig.asserttoif;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IfCanBeAssertionInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("if.can.be.assertion.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IfToAssertionVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IfToAssertionFix();
  }

  static PsiNewExpression getThrownNewException(PsiElement element) {
    if (element instanceof PsiBlockStatement) {
      final PsiStatement[] statements = ((PsiBlockStatement)element).getCodeBlock().getStatements();
      if (statements.length == 1) {
        return getThrownNewException(statements[0]);
      }
    }
    else if (element instanceof PsiThrowStatement) {
      final PsiThrowStatement throwStatement = (PsiThrowStatement)element;
      final PsiExpression exception = ParenthesesUtils.stripParentheses(throwStatement.getException());
      if (exception instanceof PsiNewExpression) {
        return (PsiNewExpression)exception;
      }
    }
    return null;
  }

  private static class IfToAssertionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      if (statement.getCondition() != null &&
          statement.getElseBranch() == null &&
          getThrownNewException(statement.getThenBranch()) != null) {
        registerStatementError(statement);
      }
    }
  }

  private static class IfToAssertionFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("if.can.be.assertion.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement parent = descriptor.getPsiElement().getParent();
      if (!(parent instanceof PsiIfStatement)) {
        return;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)parent;
      @NonNls final StringBuilder newStatementText = new StringBuilder("assert ");
      newStatementText.append(BoolUtils.getNegatedExpressionText(ifStatement.getCondition()));
      final PsiNewExpression newException = getThrownNewException(ifStatement.getThenBranch());
      final String message = getExceptionMessage(newException);
      if (message != null) {
        newStatementText.append(':').append(message);
      }
      newStatementText.append(';');
      PsiReplacementUtil.replaceStatement(ifStatement, newStatementText.toString());
    }

    private static String getExceptionMessage(PsiNewExpression newExpression) {
      if (newExpression == null) {
        return null;
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return null;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length < 1) {
        return null;
      }
      return arguments[0].getText();
    }
  }
}
