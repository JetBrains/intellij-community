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
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MergeIfAndIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new MergeIfAndPredicate();
  }

  public void processIntention(@NotNull PsiElement element) {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiIfStatement parentStatement = (PsiIfStatement)token.getParent();
    if (parentStatement == null) return;
    final PsiStatement parentThenBranch = parentStatement.getThenBranch();
    final PsiIfStatement childStatement = (PsiIfStatement)ControlFlowUtils.stripBraces(parentThenBranch);
    if (childStatement == null) return;
    final PsiExpression childCondition = childStatement.getCondition();
    if (childCondition == null) return;
    final PsiStatement childThenBranch = childStatement.getThenBranch();
    if (childThenBranch == null) return;
    final PsiExpression parentCondition = parentStatement.getCondition();
    if (parentCondition == null) return;

    CommentTracker ct = new CommentTracker();
    final String childConditionText = ParenthesesUtils.getText(ct.markUnchanged(childCondition), ParenthesesUtils.OR_PRECEDENCE);

    final String parentConditionText = ParenthesesUtils.getText(ct.markUnchanged(parentCondition), ParenthesesUtils.OR_PRECEDENCE);

    @NonNls final String statement = "if(" + parentConditionText + "&&" + childConditionText + ')' + ct.text(childThenBranch);
    ct.replaceAndRestoreComments(parentStatement, statement);
  }
}