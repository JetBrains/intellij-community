/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MergeIfOrIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new MergeIfOrPredicate();
  }

  public void processIntention(@NotNull PsiElement element) {
    final PsiJavaToken token = (PsiJavaToken)element;
    if (MergeIfOrPredicate.isMergableExplicitIf(token)) {
      replaceMergeableExplicitIf(token);
    }
    else {
      replaceMergeableImplicitIf(token);
    }
  }

  private static void replaceMergeableExplicitIf(PsiJavaToken token) {
    final PsiIfStatement parentStatement = (PsiIfStatement)token.getParent();
    assert parentStatement != null;
    final PsiIfStatement childStatement = (PsiIfStatement)parentStatement.getElseBranch();
    if (childStatement == null) {
      return;
    }
    final PsiExpression childCondition = childStatement.getCondition();
    if (childCondition == null) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    final String childConditionText = tracker.text(childCondition, ParenthesesUtils.OR_PRECEDENCE);
    final PsiExpression condition = parentStatement.getCondition();
    if (condition == null) {
      return;
    }
    final String parentConditionText = tracker.text(condition, ParenthesesUtils.OR_PRECEDENCE);
    final PsiStatement parentThenBranch = parentStatement.getThenBranch();
    if (parentThenBranch == null) {
      return;
    }
    final String parentThenBranchText = tracker.text(parentThenBranch);
    @NonNls final StringBuilder statement = new StringBuilder();
    statement.append("if(");
    statement.append(parentConditionText);
    statement.append("||");
    statement.append(childConditionText);
    statement.append(')');
    statement.append(parentThenBranchText);
    final PsiStatement childElseBranch = childStatement.getElseBranch();
    if (childElseBranch != null) {
      statement.append("\nelse ");
      statement.append(tracker.text(childElseBranch));
    }
    PsiReplacementUtil.replaceStatement(parentStatement, statement.toString(), tracker);
  }

  private static void replaceMergeableImplicitIf(PsiJavaToken token) {
    final PsiIfStatement parentStatement = (PsiIfStatement)token.getParent();
    final PsiIfStatement childStatement = (PsiIfStatement)PsiTreeUtil.skipWhitespacesForward(parentStatement);
    assert childStatement != null;
    final PsiExpression childCondition = childStatement.getCondition();
    if (childCondition == null) {
      return;
    }
    CommentTracker parentTracker = new CommentTracker();
    CommentTracker childTracker = new CommentTracker();
    final String childConditionText = childTracker.text(childCondition, ParenthesesUtils.OR_PRECEDENCE);
    final PsiExpression condition = parentStatement.getCondition();
    if (condition == null) {
      return;
    }
    final String parentConditionText = parentTracker.text(condition, ParenthesesUtils.OR_PRECEDENCE);
    final PsiStatement parentThenBranch = parentStatement.getThenBranch();
    if (parentThenBranch == null) {
      return;
    }
    final StringBuilder newStatement = new StringBuilder();
    newStatement.append("if(");
    newStatement.append(parentConditionText);
    newStatement.append("||");
    newStatement.append(childConditionText);
    newStatement.append(')');
    newStatement.append(parentTracker.text(parentThenBranch));
    final PsiStatement childElseBranch = childStatement.getElseBranch();
    if (childElseBranch != null) {
      newStatement.append("\nelse ");
      newStatement.append(childTracker.text(childElseBranch));
    }
    PsiReplacementUtil.replaceStatement(parentStatement, newStatement.toString(), parentTracker);
    childTracker.deleteAndRestoreComments(childStatement);
  }
}