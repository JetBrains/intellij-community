/*
 * Copyright 2007-2018 Bas Leijdekkers
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
package com.siyeh.ipp.whileloop;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.siyeh.ig.psiutils.BlockUtils;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ExtractWhileLoopConditionToIfStatementIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new WhileLoopPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiWhileStatement whileStatement = (PsiWhileStatement)element.getParent();
    if (whileStatement == null) {
      return;
    }
    final PsiExpression condition = whileStatement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = BoolUtils.getNegatedExpressionText(condition);
    final Project project = whileStatement.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    condition.replace(factory.createExpressionFromText("true", whileStatement));
    final PsiStatement body = whileStatement.getBody();
    final PsiStatement ifStatement = factory.createStatementFromText("if (" + conditionText + ") break;", whileStatement);
    final PsiElement newElement;
    if (body instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      newElement = codeBlock.addBefore(ifStatement, codeBlock.getFirstBodyElement());
    }
    else if (body != null) {
      final PsiStatement newStatement = BlockUtils.expandSingleStatementToBlockStatement(body);
      newElement = newStatement.getParent().addBefore(ifStatement, newStatement);
    }
    else {
      return;
    }
    CodeStyleManager.getInstance(project).reformat(newElement);
  }
}