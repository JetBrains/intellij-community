/*
 * Copyright 2006-2010 Bas Leijdekkers
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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceForLoopWithWhileLoopIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ForLoopPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    final PsiForStatement forStatement =
      (PsiForStatement)element.getParent();
    if (forStatement == null) {
      return;
    }
    final PsiStatement initialization = forStatement.getInitialization();
    if (initialization != null &&
        !(initialization instanceof PsiEmptyStatement)) {
      final PsiElement parent = forStatement.getParent();
      parent.addBefore(initialization, forStatement);
    }
    final JavaPsiFacade psiFacade =
      JavaPsiFacade.getInstance(element.getProject());
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiWhileStatement whileStatement =
      (PsiWhileStatement)factory.createStatementFromText(
        "while(true) {}", element);
    final PsiExpression forCondition = forStatement.getCondition();
    final PsiExpression whileCondition = whileStatement.getCondition();
    final PsiStatement body = forStatement.getBody();
    if (forCondition != null) {
      assert whileCondition != null;
      whileCondition.replace(forCondition);
    }
    final PsiBlockStatement blockStatement =
      (PsiBlockStatement)whileStatement.getBody();
    if (blockStatement == null) {
      return;
    }
    final PsiElement newBody;
    if (body instanceof PsiBlockStatement) {
      final PsiBlockStatement newWhileBody =
        (PsiBlockStatement)blockStatement.replace(body);
      newBody = newWhileBody.getCodeBlock();
    }
    else {
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      if (body != null && !(body instanceof PsiEmptyStatement)) {
        codeBlock.addAfter(body, codeBlock.getFirstChild());
      }
      newBody = codeBlock;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (update != null) {
      final PsiStatement[] updateStatements;
      if (update instanceof PsiExpressionListStatement) {
        final PsiExpressionListStatement expressionListStatement =
          (PsiExpressionListStatement)update;
        final PsiExpressionList expressionList =
          expressionListStatement.getExpressionList();
        final PsiExpression[] expressions =
          expressionList.getExpressions();
        updateStatements = new PsiStatement[expressions.length];
        for (int i = 0, expressionsLength = expressions.length;
             i < expressionsLength; i++) {
          final PsiExpression expression = expressions[i];
          final PsiStatement updateStatement =
            factory.createStatementFromText(
              expression.getText() + ';', element);
          updateStatements[i] = updateStatement;
        }
      }
      else {
        final PsiStatement updateStatement =
          factory.createStatementFromText(
            update.getText() + ';', element);
        updateStatements = new PsiStatement[]{updateStatement};
      }
      newBody.accept(new UpdateInserter(whileStatement, updateStatements));
      for (PsiStatement updateStatement : updateStatements) {
        newBody.addBefore(updateStatement, newBody.getLastChild());
      }
    }
    forStatement.replace(whileStatement);
  }

  private static class UpdateInserter
    extends JavaRecursiveElementWalkingVisitor {

    private final PsiWhileStatement whileStatement;
    private final PsiStatement[] updateStatements;

    private UpdateInserter(PsiWhileStatement whileStatement,
                           PsiStatement[] updateStatements) {
      this.whileStatement = whileStatement;
      this.updateStatements = updateStatements;
    }

    @Override
    public void visitContinueStatement(PsiContinueStatement statement) {
      final PsiStatement continuedStatement =
        statement.findContinuedStatement();
      if (!whileStatement.equals(continuedStatement)) {
        return;
      }
      final PsiElement parent = statement.getParent();
      if (parent == null) {
        return;
      }
      for (PsiStatement updateStatement : updateStatements) {
        parent.addBefore(updateStatement, statement);
      }
      super.visitContinueStatement(statement);
    }
  }
}