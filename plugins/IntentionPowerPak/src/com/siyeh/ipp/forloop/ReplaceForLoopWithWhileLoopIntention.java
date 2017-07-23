/*
 * Copyright 2006-2017 Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.BlockUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ReplaceForLoopWithWhileLoopIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ForLoopPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiForStatement forStatement = (PsiForStatement)element.getParent();
    if (forStatement == null) {
      return;
    }
    PsiStatement initialization = forStatement.getInitialization();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiWhileStatement whileStatement = (PsiWhileStatement)factory.createStatementFromText("while(true) {}", element);
    final PsiExpression forCondition = forStatement.getCondition();
    final PsiExpression whileCondition = whileStatement.getCondition();
    if (forCondition != null) {
      assert whileCondition != null;
      whileCondition.replace(forCondition);
    }
    final PsiBlockStatement blockStatement = (PsiBlockStatement)whileStatement.getBody();
    if (blockStatement == null) {
      return;
    }
    final PsiStatement forStatementBody = forStatement.getBody();
    final PsiElement loopBody;
    if (forStatementBody instanceof PsiBlockStatement) {
      final PsiBlockStatement newWhileBody = (PsiBlockStatement)blockStatement.replace(forStatementBody);
      loopBody = newWhileBody.getCodeBlock();
    }
    else {
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      if (forStatementBody != null && !(forStatementBody instanceof PsiEmptyStatement)) {
        codeBlock.add(forStatementBody);
      }
      loopBody = codeBlock;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (update != null) {
      final PsiStatement[] updateStatements;
      if (update instanceof PsiExpressionListStatement) {
        final PsiExpressionListStatement expressionListStatement = (PsiExpressionListStatement)update;
        final PsiExpressionList expressionList = expressionListStatement.getExpressionList();
        final PsiExpression[] expressions = expressionList.getExpressions();
        updateStatements = new PsiStatement[expressions.length];
        for (int i = 0; i < expressions.length; i++) {
          updateStatements[i] = factory.createStatementFromText(expressions[i].getText() + ';', element);
        }
      }
      else {
        final PsiStatement updateStatement = factory.createStatementFromText(update.getText() + ';', element);
        updateStatements = new PsiStatement[]{updateStatement};
      }
      final Collection<PsiContinueStatement> continueStatements = PsiTreeUtil.findChildrenOfType(loopBody, PsiContinueStatement.class);
      for (PsiContinueStatement continueStatement : continueStatements) {
        BlockUtils.addBefore(continueStatement, updateStatements);
      }
      for (PsiStatement updateStatement : updateStatements) {
        loopBody.addBefore(updateStatement, loopBody.getLastChild());
      }
    }
    if (initialization == null || initialization instanceof PsiEmptyStatement) {
      forStatement.replace(whileStatement);
    }
    else {
      initialization = (PsiStatement)initialization.copy();
      final PsiStatement newStatement = (PsiStatement)forStatement.replace(whileStatement);
      BlockUtils.addBefore(newStatement, initialization);
    }
  }
}