/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ReplaceIfWithConditionalPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiElement parent = token.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement ifStatement = (PsiIfStatement)parent;
    if (ErrorUtil.containsError(ifStatement)) {
      return false;
    }
    final PsiExpression condition = ifStatement.getCondition();
    if (condition == null) {
      return false;
    }
    if (isReplaceableAssignment(ifStatement)) {
      return true;
    }
    if (isReplaceableReturn(ifStatement)) {
      return true;
    }
    if (isReplaceableMethodCall(ifStatement)) {
      return true;
    }
    return isReplaceableImplicitReturn(ifStatement);
  }

  public static boolean isReplaceableMethodCall(PsiIfStatement ifStatement) {
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    final PsiStatement thenStatement = ControlFlowUtils.stripBraces(thenBranch);
    if (thenStatement == null) {
      return false;
    }
    final PsiStatement elseStatement = ControlFlowUtils.stripBraces(elseBranch);
    if (elseStatement == null) {
      return false;
    }
    if (!(thenStatement instanceof PsiExpressionStatement) || !(elseStatement instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement thenExpressionStatement = (PsiExpressionStatement)thenStatement;
    final PsiExpression thenExpression = thenExpressionStatement.getExpression();
    final PsiExpressionStatement elseExpressionStatement = (PsiExpressionStatement)elseStatement;
    final PsiExpression elseExpression = elseExpressionStatement.getExpression();
    if (!(thenExpression instanceof PsiMethodCallExpression) || !(elseExpression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression thenMethodCallExpression = (PsiMethodCallExpression)thenExpression;
    final PsiMethodCallExpression elseMethodCallExpression = (PsiMethodCallExpression)elseExpression;
    final PsiReferenceExpression thenMethodExpression = thenMethodCallExpression.getMethodExpression();
    final PsiReferenceExpression elseMethodExpression = elseMethodCallExpression.getMethodExpression();
    if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenMethodExpression, elseMethodExpression)) {
      return false;
    }
    final PsiExpressionList thenArgumentList = thenMethodCallExpression.getArgumentList();
    final PsiExpression[] thenArguments = thenArgumentList.getExpressions();
    final PsiExpressionList elseArgumentList = elseMethodCallExpression.getArgumentList();
    final PsiExpression[] elseArguments = elseArgumentList.getExpressions();
    if (thenArguments.length != elseArguments.length) {
      return false;
    }
    int differences = 0;
    for (int i = 0, length = thenArguments.length; i < length; i++) {
      final PsiExpression thenArgument = thenArguments[i];
      final PsiExpression elseArgument = elseArguments[i];
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenArgument, elseArgument)) {
        differences++;
      }
    }
    return differences == 1;
  }

  public static boolean isReplaceableImplicitReturn(PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    if (!(thenBranch instanceof PsiReturnStatement)) {
      return false;
    }
    final PsiReturnStatement thenReturnStatement = (PsiReturnStatement)thenBranch;
    final PsiExpression thenReturn = thenReturnStatement.getReturnValue();
    if (thenReturn == null) {
      return false;
    }
    final PsiType thenType = thenReturn.getType();
    if (thenType == null) {
      return false;
    }
    final PsiElement nextStatement = PsiTreeUtil.skipWhitespacesForward(ifStatement);
    if (!(nextStatement instanceof PsiReturnStatement)) {
      return false;
    }
    final PsiReturnStatement elseReturnStatement = (PsiReturnStatement)nextStatement;
    final PsiExpression elseReturn = elseReturnStatement.getReturnValue();
    if (elseReturn == null) {
      return false;
    }
    final PsiType elseType = elseReturn.getType();
    if (elseType == null) {
      return false;
    }
    return thenType.isAssignableFrom(elseType) || elseType.isAssignableFrom(thenType);
  }

  public static boolean isReplaceableReturn(PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ControlFlowUtils.stripBraces(elseBranch);
    if (!(thenBranch instanceof PsiReturnStatement) || !(elseBranch instanceof PsiReturnStatement)) {
      return false;
    }
    final PsiExpression thenReturn = ((PsiReturnStatement)thenBranch).getReturnValue();
    if (thenReturn == null) {
      return false;
    }
    final PsiExpression elseReturn = ((PsiReturnStatement)elseBranch).getReturnValue();
    if (elseReturn == null) {
      return false;
    }
    final PsiType thenType = thenReturn.getType();
    final PsiType elseType = elseReturn.getType();
    if (thenType == null || elseType == null) {
      return false;
    }
    return thenType.isAssignableFrom(elseType) || elseType.isAssignableFrom(thenType);
  }

  public static boolean isReplaceableAssignment(PsiIfStatement ifStatement) {
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    final PsiStatement elseBranch = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
    if (thenBranch == null || elseBranch == null) {
      return false;
    }
    if (!(thenBranch instanceof PsiExpressionStatement) || !(elseBranch instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement thenExpressionStatement = (PsiExpressionStatement)thenBranch;
    final PsiExpressionStatement elseExpressionStatement = (PsiExpressionStatement)elseBranch;
    final PsiExpression thenExpression = thenExpressionStatement.getExpression();
    final PsiExpression elseExpression = elseExpressionStatement.getExpression();
    if (!(thenExpression instanceof PsiAssignmentExpression) || !(elseExpression instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression thenAssignmentExpression = (PsiAssignmentExpression)thenExpression;
    final PsiAssignmentExpression elseAssignmentExpression = (PsiAssignmentExpression)elseExpression;
    final PsiExpression thenRhs = thenAssignmentExpression.getRExpression();
    final PsiExpression elseRhs = elseAssignmentExpression.getRExpression();
    if (thenRhs == null || elseRhs == null) {
      return false;
    }
    final IElementType thenTokenType = thenAssignmentExpression.getOperationTokenType();
    final IElementType elseTokenType = elseAssignmentExpression.getOperationTokenType();
    if (!thenTokenType.equals(elseTokenType)) {
      return false;
    }
    final PsiExpression thenLhs = thenAssignmentExpression.getLExpression();
    final PsiExpression elseLhs = elseAssignmentExpression.getLExpression();
    return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenLhs, elseLhs);
  }
}
