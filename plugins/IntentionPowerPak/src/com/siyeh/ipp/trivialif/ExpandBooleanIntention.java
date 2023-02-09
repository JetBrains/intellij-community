/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExpandBooleanIntention extends MutablyNamedIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("expand.boolean.intention.family.name");
  }

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
    if (!(element instanceof PsiStatement statement)) {
      return;
    }
    final Project project = element.getProject();
    if (ExpandBooleanPredicate.isBooleanAssignment(statement)) {
      final PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)statement;
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)assignmentStatement.getExpression();
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (rhs == null) {
        return;
      }
      final CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(rhs);
      final String lhsText = assignmentExpression.getLExpression().getText();
      final String signText = assignmentExpression.getOperationSign().getText();
      @NonNls final String newStatementText = "if(" + ((signText.length() == 2) ? lhsText + signText.charAt(0) : "") + "true)" +
                                              lhsText + "=true; else " + lhsText + "=false;";
      final PsiIfStatement newIfStatement =
        (PsiIfStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(newStatementText, element);
      final PsiExpression condition = newIfStatement.getCondition();
      assert condition != null;
      if (condition instanceof PsiBinaryExpression binaryExpression) {
        final PsiExpression operand = binaryExpression.getROperand();
        if (operand != null) {
          operand.replace(rhs);
        }
      }
      else {
        condition.replace(rhs);
      }
      final PsiElement newElement = tracker.replaceAndRestoreComments(statement, newIfStatement);
      CodeStyleManager.getInstance(project).reformat(newElement);
    }
    else if (ExpandBooleanPredicate.isBooleanReturn(statement)) {
      final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue == null) {
        return;
      }
      @NonNls final String newStatementText = "if(true) return true; else return false;";
      final PsiIfStatement newIfStatement =
        (PsiIfStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(newStatementText, element);
      final PsiExpression condition = newIfStatement.getCondition();
      assert condition != null;
      final CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(returnValue);
      condition.replace(returnValue);
      final PsiElement newElement = tracker.replaceAndRestoreComments(statement, newIfStatement);
      CodeStyleManager.getInstance(project).reformat(newElement);
    }
    else if (ExpandBooleanPredicate.isBooleanDeclaration(statement)) {
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      final PsiElement declaredElement = declarationStatement.getDeclaredElements()[0];
      if (!(declaredElement instanceof PsiLocalVariable variable)) {
        return;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        return;
      }
      final String name = variable.getName();
      @NonNls final String newStatementText = "if(true) " + name +"=true; else " + name + "=false;";
      final PsiIfStatement newIfStatement =
        (PsiIfStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(newStatementText, statement);
      final PsiExpression condition = newIfStatement.getCondition();
      assert condition != null;
      condition.replace(initializer);
      CodeStyleManager.getInstance(project).reformat(declarationStatement.getParent().addAfter(newIfStatement, declarationStatement));
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement.isInferredType() && PsiTypesUtil.replaceWithExplicitType(typeElement) == null) {
        return;
      }
      initializer.delete();
    }
  }
}