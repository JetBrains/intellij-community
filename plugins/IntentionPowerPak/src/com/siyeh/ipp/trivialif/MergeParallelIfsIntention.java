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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class MergeParallelIfsIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new MergeParallelIfsPredicate();
  }

  @Override
  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiIfStatement firstStatement = (PsiIfStatement)token.getParent();
    final PsiIfStatement secondStatement =
      (PsiIfStatement)PsiTreeUtil.skipWhitespacesForward(firstStatement);
    final String statement =
      mergeIfStatements(firstStatement, secondStatement);
    assert firstStatement != null;
    PsiReplacementUtil.replaceStatement(firstStatement, statement);
    assert secondStatement != null;
    secondStatement.delete();
  }

  private static String mergeIfStatements(PsiIfStatement firstStatement,
                                          PsiIfStatement secondStatement) {
    final PsiExpression condition = firstStatement.getCondition();
    final String conditionText;
    if (condition == null) {
      conditionText = "";
    }
    else {
      conditionText = condition.getText();
    }
    final PsiStatement firstThenBranch = firstStatement.getThenBranch();
    final PsiStatement secondThenBranch = secondStatement.getThenBranch();
    @NonNls String statement = "if(" + conditionText + ')' +
                               printStatementsInSequence(firstThenBranch,
                                                         secondThenBranch);
    final PsiStatement firstElseBranch = firstStatement.getElseBranch();
    final PsiStatement secondElseBranch = secondStatement.getElseBranch();
    if (firstElseBranch != null || secondElseBranch != null) {
      if (firstElseBranch instanceof PsiIfStatement
          && secondElseBranch instanceof PsiIfStatement
          && MergeParallelIfsPredicate.ifStatementsCanBeMerged(
        (PsiIfStatement)firstElseBranch,
        (PsiIfStatement)secondElseBranch)) {
        statement += "else " +
                     mergeIfStatements((PsiIfStatement)firstElseBranch,
                                       (PsiIfStatement)secondElseBranch);
      }
      else {
        statement += "else" +
                     printStatementsInSequence(firstElseBranch,
                                               secondElseBranch);
      }
    }
    return statement;
  }

  private static String printStatementsInSequence(PsiStatement statement1,
                                                  PsiStatement statement2) {
    if (statement1 == null) {
      return ' ' + statement2.getText();
    }
    if (statement2 == null) {
      return ' ' + statement1.getText();
    }
    final StringBuilder out = new StringBuilder();
    out.append('{');
    printStatementStripped(statement1, out);
    printStatementStripped(statement2, out);
    out.append('}');
    return out.toString();
  }

  private static void printStatementStripped(PsiStatement statement,
                                             StringBuilder out) {
    if (statement instanceof PsiBlockStatement) {
      final PsiCodeBlock block =
        ((PsiBlockStatement)statement).getCodeBlock();
      final PsiElement[] children = block.getChildren();
      for (int i = 1; i < children.length - 1; i++) {
        out.append(children[i].getText());
      }
    }
    else {
      out.append(statement.getText());
    }
  }
}