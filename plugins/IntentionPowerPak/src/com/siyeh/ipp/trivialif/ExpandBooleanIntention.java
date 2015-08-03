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
package com.siyeh.ipp.trivialif;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExpandBooleanIntention extends MutablyNamedIntention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ExpandBooleanPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    if (element instanceof PsiDeclarationStatement) {
      return IntentionPowerPackBundle.message("expand.boolean.declaration.intention.name");
    }
    else if (element instanceof PsiReturnStatement) {
      return IntentionPowerPackBundle.message("expand.boolean.return.intention.name");
    }
    return IntentionPowerPackBundle.message("expand.boolean.assignment.intention.name");
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    if (!(element instanceof PsiStatement)) {
      return;
    }
    final PsiStatement statement = (PsiStatement)element;
    if (ExpandBooleanPredicate.isBooleanAssignment(statement)) {
      final PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)statement;
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)assignmentStatement.getExpression();
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (rhs == null) {
        return;
      }
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (ErrorUtil.containsDeepError(lhs) || ErrorUtil.containsDeepError(rhs)) {
        return;
      }
      final String rhsText = rhs.getText();
      final String lhsText = lhs.getText();
      final PsiJavaToken sign = assignmentExpression.getOperationSign();
      final String signText = sign.getText();
      final String conditionText;
      if (signText.length() == 2) {
        conditionText = lhsText + signText.charAt(0) + rhsText;
      }
      else {
        conditionText = rhsText;
      }
      @NonNls final String newStatementText = "if(" + conditionText + ") " + lhsText + " = true; else " + lhsText + " = false;";
      PsiReplacementUtil.replaceStatement(statement, newStatementText);
    }
    else if (ExpandBooleanPredicate.isBooleanReturn(statement)) {
      final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue == null) {
        return;
      }
      if (ErrorUtil.containsDeepError(returnValue)) {
        return;
      }
      final String valueText = returnValue.getText();
      @NonNls final String newStatementText = "if(" + valueText + ") return true; else return false;";
      PsiReplacementUtil.replaceStatement(statement, newStatementText);
    }
    else if (ExpandBooleanPredicate.isBooleanDeclaration(statement)) {
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      final PsiElement declaredElement = declarationStatement.getDeclaredElements()[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return;
      }
      final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
      final PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        return;
      }
      final String name = variable.getName();
      @NonNls final String newStatementText = "if(" + initializer.getText() + ") " + name +"=true; else " + name + "=false;";
      final Project project = statement.getProject();
      final PsiStatement newStatement = JavaPsiFacade.getElementFactory(project).createStatementFromText(newStatementText, statement);
      final PsiElement newElement = declarationStatement.getParent().addAfter(newStatement, declarationStatement);
      CodeStyleManager.getInstance(project).reformat(newElement);
      initializer.delete();
    }
  }
}