/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EquivalenceChecker {
  protected static final Match EXACT_MATCH = new Match(true);
  protected static final Match EXACT_MISMATCH = new Match(false);
  private static final EquivalenceChecker ourCanonicalPsiEquivalence = new EquivalenceChecker();

  public static EquivalenceChecker getCanonicalPsiEquivalence() {
    return ourCanonicalPsiEquivalence;
  }

  public static class Match {
    private final PsiElement myLeftDiff;
    private final PsiElement myRightDiff;
    private final Boolean myExactlyMatches;

    Match(boolean exactlyMatches) {
      myExactlyMatches = exactlyMatches;
      myLeftDiff = null;
      myRightDiff = null;
    }

    Match(PsiElement leftDiff, PsiElement rightDiff) {
      myExactlyMatches = null;
      myLeftDiff = leftDiff;
      myRightDiff = rightDiff;
    }

    public PsiElement getLeftDiff() {
      return myLeftDiff;
    }

    public PsiElement getRightDiff() {
      return myRightDiff;
    }

    public boolean isPartialMatch() {
      return myExactlyMatches == null;
    }

    public boolean isExactMatch() {
      return myExactlyMatches != null && myExactlyMatches;
    }

    public boolean isExactMismatch() {
      return myExactlyMatches != null && !myExactlyMatches;
    }

    Match partialIfExactMismatch(PsiElement left, PsiElement right) {
      return this == EXACT_MISMATCH ? new Match(left, right) : this;
    }

    static Match exact(boolean exactMatches) {
      return exactMatches ? EXACT_MATCH : EXACT_MISMATCH;
    }

    Match combine(Match other) {
      if (other.isExactMismatch() || isExactMatch()) {
        return other;
      }
      if (isExactMismatch() || other.isExactMatch()) {
        return this;
      }
      return EXACT_MISMATCH;
    }
  }

  public boolean statementsAreEquivalent(@Nullable PsiStatement statement1, @Nullable PsiStatement statement2) {
    return statementsMatch(statement1, statement2).isExactMatch();
  }

  public Match statementsMatch(@Nullable PsiStatement statement1, @Nullable PsiStatement statement2) {
    statement1 = ControlFlowUtils.stripBraces(statement1);
    statement2 = ControlFlowUtils.stripBraces(statement2);
    if (statement1 == null) {
      return Match.exact(statement2 == null);
    }
    if (statement2 == null) {
      return EXACT_MISMATCH;
    }
    if (statement1.getClass() != statement2.getClass()) {
        return EXACT_MISMATCH;
    }
    if (statement1 instanceof PsiAssertStatement) {
      return assertStatementsMatch((PsiAssertStatement)statement1, (PsiAssertStatement)statement2);
    }
    if (statement1 instanceof PsiBlockStatement) {
      return blockStatementsMatch((PsiBlockStatement)statement1, (PsiBlockStatement)statement2);
    }
    if (statement1 instanceof PsiBreakStatement) {
      return breakStatementsMatch((PsiBreakStatement)statement1, (PsiBreakStatement)statement2);
    }
    if (statement1 instanceof PsiContinueStatement) {
      return continueStatementsMatch((PsiContinueStatement)statement1, (PsiContinueStatement)statement2);
    }
    if (statement1 instanceof PsiDeclarationStatement) {
      return declarationStatementsMatch((PsiDeclarationStatement)statement1, (PsiDeclarationStatement)statement2);
    }
    if (statement1 instanceof PsiDoWhileStatement) {
      return doWhileStatementsMatch((PsiDoWhileStatement)statement1, (PsiDoWhileStatement)statement2);
    }
    if (statement1 instanceof PsiEmptyStatement) {
      return EXACT_MATCH;
    }
    if (statement1 instanceof PsiExpressionListStatement) {
      return expressionListStatementsMatch((PsiExpressionListStatement)statement1, (PsiExpressionListStatement)statement2);
    }
    if (statement1 instanceof PsiExpressionStatement) {
      return expressionStatementsMatch((PsiExpressionStatement)statement1, (PsiExpressionStatement)statement2);
    }
    if (statement1 instanceof PsiForStatement) {
      return forStatementsMatch((PsiForStatement)statement1, (PsiForStatement)statement2);
    }
    if (statement1 instanceof PsiForeachStatement) {
      return forEachStatementsMatch((PsiForeachStatement)statement1, (PsiForeachStatement)statement2);
    }
    if (statement1 instanceof PsiIfStatement) {
      return ifStatementsMatch((PsiIfStatement)statement1, (PsiIfStatement)statement2);
    }
    if (statement1 instanceof PsiLabeledStatement) {
      return labeledStatementsMatch((PsiLabeledStatement)statement1, (PsiLabeledStatement)statement2);
    }
    if (statement1 instanceof PsiReturnStatement) {
      return returnStatementsMatch((PsiReturnStatement)statement1, (PsiReturnStatement)statement2);
    }
    if (statement1 instanceof PsiSwitchStatement) {
      return switchStatementsMatch((PsiSwitchStatement)statement1, (PsiSwitchStatement)statement2);
    }
    if (statement1 instanceof PsiSwitchLabelStatement) {
      return switchLabelStatementsMatch((PsiSwitchLabelStatement)statement1, (PsiSwitchLabelStatement)statement2);
    }
    if (statement1 instanceof PsiSynchronizedStatement) {
      return synchronizedStatementsMatch((PsiSynchronizedStatement)statement1, (PsiSynchronizedStatement)statement2);
    }
    if (statement1 instanceof PsiThrowStatement) {
      return throwStatementsMatch((PsiThrowStatement)statement1, (PsiThrowStatement)statement2);
    }
    if (statement1 instanceof PsiTryStatement) {
      return tryStatementsMatch((PsiTryStatement)statement1, (PsiTryStatement)statement2);
    }
    if (statement1 instanceof PsiWhileStatement) {
      return whileStatementsMatch((PsiWhileStatement)statement1, (PsiWhileStatement)statement2);
    }
    final String text1 = statement1.getText();
    final String text2 = statement2.getText();
    return Match.exact(text1.equals(text2));
  }

  protected Match declarationStatementsMatch(@NotNull PsiDeclarationStatement statement1, @NotNull PsiDeclarationStatement statement2) {
    final PsiElement[] elements1 = statement1.getDeclaredElements();
    final List<PsiLocalVariable> vars1 =
      new ArrayList<>(elements1.length);
    for (PsiElement anElement : elements1) {
      if (anElement instanceof PsiLocalVariable) {
        vars1.add((PsiLocalVariable)anElement);
      }
    }
    final PsiElement[] elements2 = statement2.getDeclaredElements();
    final List<PsiLocalVariable> vars2 =
      new ArrayList<>(elements2.length);
    for (PsiElement anElement : elements2) {
      if (anElement instanceof PsiLocalVariable) {
        vars2.add((PsiLocalVariable)anElement);
      }
    }
    final int size = vars1.size();
    if (size != vars2.size()) {
      return EXACT_MISMATCH;
    }
    for (int i = 0; i < size; i++) {
      final PsiLocalVariable var1 = vars1.get(i);
      final PsiLocalVariable var2 = vars2.get(i);
      if (!localVariablesAreEquivalent(var1, var2).isExactMatch()) {
        return EXACT_MISMATCH;
      }
    }
    return EXACT_MATCH;
  }

  protected Match localVariablesAreEquivalent(@NotNull PsiLocalVariable localVariable1, @NotNull PsiLocalVariable localVariable2) {
    final PsiType type1 = localVariable1.getType();
    final PsiType type2 = localVariable2.getType();
    if (!typesAreEquivalent(type1, type2)) {
      return EXACT_MISMATCH;
    }
    final String name1 = localVariable1.getName();
    final String name2 = localVariable2.getName();
    if (name1 == null) {
      return Match.exact(name2 == null);
    }
    if (!name1.equals(name2)) {
      return EXACT_MISMATCH;
    }
    final PsiExpression initializer1 = localVariable1.getInitializer();
    final PsiExpression initializer2 = localVariable2.getInitializer();
    return expressionsMatch(initializer1, initializer2).partialIfExactMismatch(initializer1, initializer2);
  }

  protected Match tryStatementsMatch(@NotNull PsiTryStatement statement1, @NotNull PsiTryStatement statement2) {
    final PsiCodeBlock tryBlock1 = statement1.getTryBlock();
    final PsiCodeBlock tryBlock2 = statement2.getTryBlock();
    if (!codeBlocksMatch(tryBlock1, tryBlock2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiCodeBlock finallyBlock1 = statement1.getFinallyBlock();
    final PsiCodeBlock finallyBlock2 = statement2.getFinallyBlock();
    if (!codeBlocksMatch(finallyBlock1, finallyBlock2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiCodeBlock[] catchBlocks1 = statement1.getCatchBlocks();
    final PsiCodeBlock[] catchBlocks2 = statement2.getCatchBlocks();
    if (catchBlocks1.length != catchBlocks2.length) {
      return EXACT_MISMATCH;
    }
    for (int i = 0; i < catchBlocks2.length; i++) {
      if (!codeBlocksMatch(catchBlocks1[i], catchBlocks2[i]).isExactMatch()) {
        return EXACT_MISMATCH;
      }
    }
    final PsiResourceList resourceList1 = statement1.getResourceList();
    final PsiResourceList resourceList2 = statement2.getResourceList();
    if (resourceList1 != null) {
      if (resourceList2 == null) {
        return EXACT_MISMATCH;
      }
      if (resourceList1.getResourceVariablesCount() != resourceList2.getResourceVariablesCount()) {
        return EXACT_MISMATCH;
      }
      final List<PsiResourceListElement> resources1 = PsiTreeUtil.getChildrenOfTypeAsList(resourceList1, PsiResourceListElement.class);
      final List<PsiResourceListElement> resources2 = PsiTreeUtil.getChildrenOfTypeAsList(resourceList2, PsiResourceListElement.class);
      for (int i = 0, size = resources1.size(); i < size; i++) {
        final PsiResourceListElement resource1 = resources1.get(i);
        final PsiResourceListElement resource2 = resources2.get(i);
        if (resource1 instanceof PsiResourceVariable && resource2 instanceof PsiResourceVariable) {
          if (!localVariablesAreEquivalent((PsiLocalVariable)resource1, (PsiLocalVariable)resource2).isExactMatch()) {
            return EXACT_MISMATCH;
          }
        }
        else if (resource1 instanceof PsiResourceExpression && resource2 instanceof PsiResourceExpression) {
          if (!expressionsMatch(((PsiResourceExpression)resource1).getExpression(),
                                                ((PsiResourceExpression)resource2).getExpression()).isExactMatch()) {
            return EXACT_MISMATCH;
          }
        }
        else {
          return EXACT_MISMATCH;
        }
      }
    }
    else if (resourceList2 != null) {
      return EXACT_MISMATCH;
    }
    final PsiParameter[] catchParameters1 = statement1.getCatchBlockParameters();
    final PsiParameter[] catchParameters2 = statement2.getCatchBlockParameters();
    if (catchParameters1.length != catchParameters2.length) {
      return EXACT_MISMATCH;
    }
    for (int i = 0; i < catchParameters2.length; i++) {
      if (!parametersAreEquivalent(catchParameters2[i], catchParameters1[i]).isExactMatch()) {
        return EXACT_MISMATCH;
      }
    }
    return EXACT_MATCH;
  }

  protected Match parametersAreEquivalent(@NotNull PsiParameter parameter1, @NotNull PsiParameter parameter2) {
    final PsiType type1 = parameter1.getType();
    final PsiType type2 = parameter2.getType();
    if (!typesAreEquivalent(type1, type2)) {
      return EXACT_MISMATCH;
    }
    final String name1 = parameter1.getName();
    final String name2 = parameter2.getName();
    if (name1 == null) {
      return Match.exact(name2 == null);
    }
    return Match.exact(name1.equals(name2));
  }

  public boolean typesAreEquivalent(@Nullable PsiType type1, @Nullable PsiType type2) {
    if (type1 == null) {
      return type2 == null;
    }
    if (type2 == null) {
      return false;
    }
    final String type1Text = type1.getCanonicalText();
    final String type2Text = type2.getCanonicalText();
    return type1Text.equals(type2Text);
  }

  protected Match whileStatementsMatch(@NotNull PsiWhileStatement statement1, @NotNull PsiWhileStatement statement2) {
    final PsiExpression condition1 = statement1.getCondition();
    final PsiExpression condition2 = statement2.getCondition();
    final PsiStatement body1 = statement1.getBody();
    final PsiStatement body2 = statement2.getBody();
    final Match conditionEquivalence = expressionsMatch(condition1, condition2);
    final Match bodyEquivalence = statementsMatch(body1, body2);

    return getComplexElementDecision(bodyEquivalence, conditionEquivalence, body1, body2, condition1, condition2);
  }

  protected Match forStatementsMatch(@NotNull PsiForStatement statement1, @NotNull PsiForStatement statement2) {
    final PsiExpression condition1 = statement1.getCondition();
    final PsiExpression condition2 = statement2.getCondition();
    if (!expressionsMatch(condition1, condition2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiStatement initialization1 = statement1.getInitialization();
    final PsiStatement initialization2 = statement2.getInitialization();
    if (!statementsMatch(initialization1, initialization2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiStatement update1 = statement1.getUpdate();
    final PsiStatement update2 = statement2.getUpdate();
    if (!statementsMatch(update1, update2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiStatement body1 = statement1.getBody();
    final PsiStatement body2 = statement2.getBody();
    return statementsMatch(body1, body2).partialIfExactMismatch(body1, body2);
  }

  protected Match forEachStatementsMatch(@NotNull PsiForeachStatement statement1, @NotNull PsiForeachStatement statement2) {
    final PsiExpression value1 = statement1.getIteratedValue();
    final PsiExpression value2 = statement2.getIteratedValue();
    if (!expressionsMatch(value1, value2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiParameter parameter1 = statement1.getIterationParameter();
    final PsiParameter parameter2 = statement1.getIterationParameter();
    final String name1 = parameter1.getName();
    if (name1 == null) {
      return Match.exact(parameter2.getName() == null);
    }
    if (!name1.equals(parameter2.getName())) {
      return EXACT_MISMATCH;
    }
    final PsiType type1 = parameter1.getType();
    if (!type1.equals(parameter2.getType())) {
      return EXACT_MISMATCH;
    }
    final PsiStatement body1 = statement1.getBody();
    final PsiStatement body2 = statement2.getBody();
    return statementsMatch(body1, body2).partialIfExactMismatch(body1, body2);
  }

  protected Match switchStatementsMatch(@NotNull PsiSwitchStatement statement1, @NotNull PsiSwitchStatement statement2) {
    final PsiExpression switchExpression1 = statement1.getExpression();
    final PsiExpression switchExpression2 = statement2.getExpression();
    final PsiCodeBlock body1 = statement1.getBody();
    final PsiCodeBlock body2 = statement2.getBody();
    final Match bodyEq = codeBlocksMatch(body1, body2);
    if (bodyEq != EXACT_MATCH) {
      return EXACT_MISMATCH;
    }
    return expressionsMatch(switchExpression1, switchExpression2).partialIfExactMismatch(switchExpression1, switchExpression2);
  }

  protected Match doWhileStatementsMatch(@NotNull PsiDoWhileStatement statement1, @NotNull PsiDoWhileStatement statement2) {
    final PsiExpression condition1 = statement1.getCondition();
    final PsiExpression condition2 = statement2.getCondition();
    final PsiStatement body1 = statement1.getBody();
    final PsiStatement body2 = statement2.getBody();
    final Match conditionEq = expressionsMatch(condition1, condition2);
    final Match bodyEq = statementsMatch(body1, body2);
    return getComplexElementDecision(bodyEq, conditionEq, body1, body2, condition1, condition2);
  }

  protected Match assertStatementsMatch(@NotNull PsiAssertStatement statement1, @NotNull PsiAssertStatement statement2) {
    final PsiExpression condition1 = statement1.getAssertCondition();
    final PsiExpression condition2 = statement2.getAssertCondition();
    final PsiExpression description1 = statement1.getAssertDescription();
    final PsiExpression description2 = statement2.getAssertDescription();
    final Match condEq = expressionsMatch(condition1, condition2);
    final Match exprEq = expressionsMatch(description1, description2);
    return getComplexElementDecision(condEq, exprEq, condition1, condition2, description1, description2);
  }

  protected Match synchronizedStatementsMatch(@NotNull PsiSynchronizedStatement statement1, @NotNull PsiSynchronizedStatement statement2) {
    final PsiExpression lock1 = statement1.getLockExpression();
    final PsiExpression lock2 = statement2.getLockExpression();
    final PsiCodeBlock body1 = statement1.getBody();
    final PsiCodeBlock body2 = statement2.getBody();
    final Match lockEq = expressionsMatch(lock1, lock2);
    final Match blockEq = codeBlocksMatch(body1, body2);
    return getComplexElementDecision(blockEq, lockEq, body1, body2, lock1, lock2);
  }

  protected Match blockStatementsMatch(@NotNull PsiBlockStatement statement1, @NotNull PsiBlockStatement statement2) {
    final PsiCodeBlock block1 = statement1.getCodeBlock();
    final PsiCodeBlock block2 = statement2.getCodeBlock();
    return codeBlocksMatch(block1, block2);
  }

  protected Match breakStatementsMatch(@NotNull PsiBreakStatement statement1, @NotNull PsiBreakStatement statement2) {
    final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
    final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
    if (identifier1 == null) {
      return Match.exact(identifier2 == null);
    }
    if (identifier2 == null) {
      return EXACT_MISMATCH;
    }
    final String text1 = identifier1.getText();
    final String text2 = identifier2.getText();
    return Match.exact(text1.equals(text2));
  }

  protected Match continueStatementsMatch(@NotNull PsiContinueStatement statement1, @NotNull PsiContinueStatement statement2) {
    final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
    final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
    if (identifier1 == null) {
      return Match.exact(identifier2 == null);
    }
    if (identifier2 == null) {
      return EXACT_MISMATCH;
    }
    final String text1 = identifier1.getText();
    final String text2 = identifier2.getText();
    return Match.exact(text1.equals(text2));
  }

  protected Match switchLabelStatementsMatch(@NotNull PsiSwitchLabelStatement statement1, @NotNull PsiSwitchLabelStatement statement2) {
    if (statement1.isDefaultCase()) {
      return Match.exact(statement2.isDefaultCase());
    }
    if (statement2.isDefaultCase()) {
      return EXACT_MISMATCH;
    }
    final PsiExpression caseExpression1 = statement1.getCaseValue();
    final PsiExpression caseExpression2 = statement2.getCaseValue();
    return expressionsMatch(caseExpression1, caseExpression2).partialIfExactMismatch(caseExpression1, caseExpression2);
  }

  protected Match labeledStatementsMatch(@NotNull PsiLabeledStatement statement1, @NotNull PsiLabeledStatement statement2) {
    final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
    final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
    final String text1 = identifier1.getText();
    final String text2 = identifier2.getText();
    return Match.exact(text1.equals(text2));
  }

  public boolean codeBlocksAreEquivalent(@Nullable PsiCodeBlock block1, @Nullable PsiCodeBlock block2) {
    return codeBlocksMatch(block1, block2).isExactMatch();
  }

  protected Match codeBlocksMatch(@Nullable PsiCodeBlock block1, @Nullable PsiCodeBlock block2) {
    if (block1 == null && block2 == null) {
      return EXACT_MATCH;
    }
    if (block1 == null || block2 == null) {
      return EXACT_MISMATCH;
    }
    final PsiStatement[] statements1 = block1.getStatements();
    final PsiStatement[] statements2 = block2.getStatements();
    if (statements2.length != statements1.length) {
      return EXACT_MISMATCH;
    }
    for (int i = 0; i < statements2.length; i++) {
      if (!statementsMatch(statements2[i], statements1[i]).isExactMatch()) {
        return EXACT_MISMATCH;
      }
    }
    return EXACT_MATCH;
  }

  protected Match ifStatementsMatch(@NotNull PsiIfStatement statement1, @NotNull PsiIfStatement statement2) {
    final PsiExpression condition1 = statement1.getCondition();
    final PsiExpression condition2 = statement2.getCondition();
    final PsiStatement thenBranch1 = statement1.getThenBranch();
    final PsiStatement thenBranch2 = statement2.getThenBranch();
    final PsiStatement elseBranch1 = statement1.getElseBranch();
    final PsiStatement elseBranch2 = statement2.getElseBranch();
    final Match conditionEq = expressionsMatch(condition1, condition2);
    final Match thenEq = statementsMatch(thenBranch1, thenBranch2);
    final Match elseEq = statementsMatch(elseBranch1, elseBranch2);
    if (conditionEq == EXACT_MATCH && thenEq == EXACT_MATCH && elseEq == EXACT_MATCH) {
      return EXACT_MATCH;
    }
    return EXACT_MISMATCH;
  }

  protected Match expressionStatementsMatch(@NotNull PsiExpressionStatement statement1, @NotNull PsiExpressionStatement statement2) {
    final PsiExpression expression1 = statement1.getExpression();
    final PsiExpression expression2 = statement2.getExpression();
    return expressionsMatch(expression1, expression2);
  }

  protected Match returnStatementsMatch(@NotNull PsiReturnStatement statement1, @NotNull PsiReturnStatement statement2) {
    final PsiExpression returnValue1 = statement1.getReturnValue();
    final PsiExpression returnValue2 = statement2.getReturnValue();
    final Match match = expressionsMatch(returnValue1, returnValue2);
    if (match.isExactMismatch()) {
      return new Match(returnValue1, returnValue2);
    }
    return match;
  }

  protected Match throwStatementsMatch(@NotNull PsiThrowStatement statement1, @NotNull PsiThrowStatement statement2) {
    final PsiExpression exception1 = statement1.getException();
    final PsiExpression exception2 = statement2.getException();
    return expressionsMatch(exception1, exception2);
  }

  protected Match expressionListStatementsMatch(@NotNull PsiExpressionListStatement statement1, @NotNull PsiExpressionListStatement statement2) {
    final PsiExpressionList expressionList1 =
      statement1.getExpressionList();
    final PsiExpression[] expressions1 = expressionList1.getExpressions();
    final PsiExpressionList expressionList2 =
      statement2.getExpressionList();
    final PsiExpression[] expressions2 = expressionList2.getExpressions();
    return expressionsAreEquivalent(expressions1, expressions2);
  }

  public boolean expressionsAreEquivalent(@Nullable PsiExpression expression1, @Nullable PsiExpression expression2) {
    return expressionsMatch(expression1, expression2).isExactMatch();
  }

  public Match expressionsMatch(@Nullable PsiExpression expression1, @Nullable PsiExpression expression2) {
    expression1 = ParenthesesUtils.stripParentheses(expression1);
    expression2 = ParenthesesUtils.stripParentheses(expression2);
    if (expression1 == null) {
      return Match.exact(expression2 == null);
    }
    if (expression2 == null) {
      return EXACT_MISMATCH;
    }
    if (expression1.getClass() != expression2.getClass()) {
      return EXACT_MISMATCH;
    }
    if (expression1 instanceof PsiThisExpression) {
      return EXACT_MATCH;
    }
    if (expression1 instanceof PsiSuperExpression) {
      return EXACT_MATCH;
    }
    if (expression1 instanceof PsiLiteralExpression) {
      return literalExpressionsMatch((PsiLiteralExpression)expression1, (PsiLiteralExpression)expression2);
    }
    if (expression1 instanceof PsiClassObjectAccessExpression) {
      return classObjectAccessExpressionsMatch((PsiClassObjectAccessExpression)expression1,
                                                               (PsiClassObjectAccessExpression)expression2);
    }
    if (expression1 instanceof PsiReferenceExpression) {
      return referenceExpressionsMatch((PsiReferenceExpression)expression1, (PsiReferenceExpression)expression2);
    }
    if (expression1 instanceof PsiMethodCallExpression) {
      return methodCallExpressionsMatch((PsiMethodCallExpression)expression1, (PsiMethodCallExpression)expression2);
    }
    if (expression1 instanceof PsiNewExpression) {
      return newExpressionsMatch((PsiNewExpression)expression1, (PsiNewExpression)expression2);
    }
    if (expression1 instanceof PsiArrayInitializerExpression) {
      return arrayInitializerExpressionsMatch((PsiArrayInitializerExpression)expression1,
                                                              (PsiArrayInitializerExpression)expression2);
    }
    if (expression1 instanceof PsiTypeCastExpression) {
      return typeCastExpressionsMatch((PsiTypeCastExpression)expression1, (PsiTypeCastExpression)expression2);
    }
    if (expression1 instanceof PsiArrayAccessExpression) {
      return arrayAccessExpressionsMatch((PsiArrayAccessExpression)expression2, (PsiArrayAccessExpression)expression1);
    }
    if (expression1 instanceof PsiPrefixExpression) {
      return prefixExpressionsMatch((PsiPrefixExpression)expression1, (PsiPrefixExpression)expression2);
    }
    if (expression1 instanceof PsiPostfixExpression) {
      return postfixExpressionsMatch((PsiPostfixExpression)expression1, (PsiPostfixExpression)expression2);
    }
    if (expression1 instanceof PsiBinaryExpression) {
      return binaryExpressionsMatch((PsiBinaryExpression)expression1, (PsiBinaryExpression)expression2);
    }
    if (expression1 instanceof PsiPolyadicExpression) {
      return polyadicExpressionsMatch((PsiPolyadicExpression)expression1, (PsiPolyadicExpression)expression2);
    }
    if (expression1 instanceof PsiAssignmentExpression) {
      return assignmentExpressionsMatch((PsiAssignmentExpression)expression1, (PsiAssignmentExpression)expression2);
    }
    if (expression1 instanceof PsiConditionalExpression) {
      return conditionalExpressionsMatch((PsiConditionalExpression)expression1, (PsiConditionalExpression)expression2);
    }
    if (expression1 instanceof PsiInstanceOfExpression) {
      return instanceOfExpressionsMatch((PsiInstanceOfExpression)expression1, (PsiInstanceOfExpression)expression2);
    }
    if (expression1 instanceof PsiLambdaExpression) {
      return lambdaExpressionsMatch((PsiLambdaExpression)expression1, (PsiLambdaExpression)expression2);
    }
    return EXACT_MISMATCH;
  }

  protected Match lambdaExpressionsMatch(PsiLambdaExpression expression1, PsiLambdaExpression expression2) {
    final PsiParameterList parameterList1 = expression1.getParameterList();
    final PsiParameterList parameterList2 = expression2.getParameterList();
    final PsiParameter[] parameters1 = parameterList1.getParameters();
    final PsiParameter[] parameters2 = parameterList2.getParameters();
    if (parameters1.length != parameters2.length) {
      return EXACT_MISMATCH;
    }
    for (int i = 0, length = parameters1.length; i < length; i++) {
      if (!parametersAreEquivalent(parameters1[i], parameters2[i]).isExactMatch()) {
        return EXACT_MISMATCH;
      }
    }
    final PsiElement body1 = unwrapLambdaBody(expression1.getBody());
    final PsiElement body2 = unwrapLambdaBody(expression2.getBody());
    if (body1 instanceof PsiCodeBlock && body2 instanceof PsiCodeBlock) {
      return codeBlocksMatch((PsiCodeBlock)body1, (PsiCodeBlock)body2);
    }
    else if (body1 instanceof PsiExpression && body2 instanceof PsiExpression) {
      return expressionsMatch((PsiExpression)body1, (PsiExpression)body2);
    }
    return EXACT_MISMATCH;
  }

  private static PsiElement unwrapLambdaBody(PsiElement element) {
    while (element instanceof PsiCodeBlock) {
      final PsiCodeBlock codeBlock = (PsiCodeBlock)element;
      final PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length != 1) {
        break;
      }
      final PsiStatement statement = statements[0];
      if (statement instanceof PsiReturnStatement) {
        return ((PsiReturnStatement)statement).getReturnValue();
      }
      else if (statement instanceof PsiExpressionStatement) {
        return ((PsiExpressionStatement)statement).getExpression();
      }
      else if (statement instanceof PsiBlockStatement) {
        element = ((PsiBlockStatement)statement).getCodeBlock();
      }
      else {
        break;
      }
    }
    return element;
  }

  protected Match literalExpressionsMatch(PsiLiteralExpression expression1, PsiLiteralExpression expression2) {
    final Object value1 = expression1.getValue();
    final Object value2 = expression2.getValue();
    if (value1 == null) {
      return Match.exact(value2 == null);
    }
    if (value2 == null) {
      return EXACT_MISMATCH;
    }
    return Match.exact(value1.equals(value2));
  }

  protected Match classObjectAccessExpressionsMatch(PsiClassObjectAccessExpression expression1,
                                                                    PsiClassObjectAccessExpression expression2) {
    final PsiTypeElement operand1 = expression1.getOperand();
    final PsiTypeElement operand2 = expression2.getOperand();
    return typeElementsAreEquivalent(operand1, operand2);
  }

  protected Match referenceExpressionsMatch(PsiReferenceExpression referenceExpression1, PsiReferenceExpression referenceExpression2) {
    final PsiElement element1 = referenceExpression1.resolve();
    final PsiElement element2 = referenceExpression2.resolve();
    if (element1 != null) {
      if (!element1.equals(element2)) {
        return EXACT_MISMATCH;
      }
    }
    else {
      return EXACT_MISMATCH; // incomplete code
    }
    if (element1 instanceof PsiMember) {
      final PsiMember member1 = (PsiMember)element1;
      if (member1.hasModifierProperty(PsiModifier.STATIC)) {
        return EXACT_MATCH;
      }
      if (member1 instanceof PsiClass) {
        return EXACT_MATCH;
      }
    }
    else {
      return EXACT_MATCH;
    }
    final PsiExpression qualifier1 = ParenthesesUtils.stripParentheses(referenceExpression1.getQualifierExpression());
    final PsiExpression qualifier2 = ParenthesesUtils.stripParentheses(referenceExpression2.getQualifierExpression());
    if (qualifier1 != null && !(qualifier1 instanceof PsiThisExpression || qualifier1 instanceof PsiSuperExpression)) {
      if (qualifier2 == null) {
        return EXACT_MISMATCH;
      }
      return expressionsMatch(qualifier1, qualifier2);
    }
    else {
      if (qualifier2 != null && !(qualifier2 instanceof PsiThisExpression || qualifier2 instanceof PsiSuperExpression)) {
        return EXACT_MISMATCH;
      }
    }
    return EXACT_MATCH;
  }

  protected Match instanceOfExpressionsMatch(PsiInstanceOfExpression instanceOfExpression1, PsiInstanceOfExpression instanceOfExpression2) {
    final PsiExpression operand1 = instanceOfExpression1.getOperand();
    final PsiExpression operand2 = instanceOfExpression2.getOperand();
    if (!expressionsMatch(operand1, operand2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiTypeElement typeElement1 = instanceOfExpression1.getCheckType();
    final PsiTypeElement typeElement2 = instanceOfExpression2.getCheckType();
    return typeElementsAreEquivalent(typeElement1, typeElement2);
  }

  protected Match typeElementsAreEquivalent(PsiTypeElement typeElement1, PsiTypeElement typeElement2) {
    if (typeElement1 == null) {
      return Match.exact(typeElement2 == null);
    }
    if (typeElement2 == null) {
      return EXACT_MISMATCH;
    }
    final PsiType type1 = typeElement1.getType();
    final PsiType type2 = typeElement2.getType();
    return Match.exact(typesAreEquivalent(type1, type2));
  }

  protected Match methodCallExpressionsMatch(@NotNull PsiMethodCallExpression methodCallExpression1, @NotNull PsiMethodCallExpression methodCallExpression2) {
    final PsiReferenceExpression methodExpression1 = methodCallExpression1.getMethodExpression();
    final PsiReferenceExpression methodExpression2 = methodCallExpression2.getMethodExpression();
    Match match = expressionsMatch(methodExpression1, methodExpression2);
    if (match.isExactMismatch()) {
      return EXACT_MISMATCH;
    }
    final PsiExpressionList argumentList1 = methodCallExpression1.getArgumentList();
    final PsiExpression[] args1 = argumentList1.getExpressions();
    final PsiExpressionList argumentList2 = methodCallExpression2.getArgumentList();
    final PsiExpression[] args2 = argumentList2.getExpressions();
    match = match.combine(expressionsAreEquivalent(args1, args2));

    if (args1.length != 0 && match.isPartialMatch()) {
      final PsiElement leftDiff = match.getLeftDiff();
      final PsiExpression lastArg = args1[args1.length - 1];
      if (Comparing.equal(leftDiff, lastArg)) {
        final PsiType type1 = lastArg.getType();
        final PsiType type2 = args2[args2.length - 1].getType();
        if (type2 instanceof PsiArrayType && !(type1 instanceof PsiArrayType)) {
          return EXACT_MISMATCH;
        }
        if (type1 instanceof PsiArrayType && !(type2 instanceof PsiArrayType)) {
          return EXACT_MISMATCH;
        }
      }
    }

    return match;
  }

  protected Match newExpressionsMatch(@NotNull PsiNewExpression newExpression1, @NotNull PsiNewExpression newExpression2) {
    final PsiJavaCodeReferenceElement classReference1 =
      newExpression1.getClassReference();
    final PsiJavaCodeReferenceElement classReference2 =
      newExpression2.getClassReference();
    if (classReference1 == null || classReference2 == null) {
      return EXACT_MISMATCH;
    }
    final PsiElement target1 = classReference1.resolve();
    if (target1 == null) {
      return EXACT_MISMATCH;
    }
    final PsiElement target2 = classReference2.resolve();
    if (!target1.equals(target2)) {
      return EXACT_MISMATCH;
    }
    final PsiExpression[] arrayDimensions1 =
      newExpression1.getArrayDimensions();
    final PsiExpression[] arrayDimensions2 =
      newExpression2.getArrayDimensions();
    if (!expressionsAreEquivalent(arrayDimensions1, arrayDimensions2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiArrayInitializerExpression arrayInitializer1 =
      newExpression1.getArrayInitializer();
    final PsiArrayInitializerExpression arrayInitializer2 =
      newExpression2.getArrayInitializer();
    if (!expressionsMatch(arrayInitializer1, arrayInitializer2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiMethod constructor1 = newExpression1.resolveConstructor();
    final PsiMethod constructor2 = newExpression2.resolveConstructor();
    if (!Comparing.equal(constructor1, constructor2)) {
      return EXACT_MISMATCH;
    }
    final PsiExpression qualifier1 = newExpression1.getQualifier();
    final PsiExpression qualifier2 = newExpression2.getQualifier();
    if (!expressionsMatch(qualifier1, qualifier2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiExpressionList argumentList1 = newExpression1.getArgumentList();
    final PsiExpression[] args1 = argumentList1 == null ? null : argumentList1.getExpressions();
    final PsiExpressionList argumentList2 = newExpression2.getArgumentList();
    final PsiExpression[] args2 = argumentList2 == null ? null : argumentList2.getExpressions();
    return expressionsAreEquivalent(args1, args2);
  }

  protected Match arrayInitializerExpressionsMatch(@NotNull PsiArrayInitializerExpression arrayInitializerExpression1, @NotNull PsiArrayInitializerExpression arrayInitializerExpression2) {
    final PsiExpression[] initializers1 =
      arrayInitializerExpression1.getInitializers();
    final PsiExpression[] initializers2 =
      arrayInitializerExpression2.getInitializers();
    return expressionsAreEquivalent(initializers1, initializers2);
  }

  protected Match typeCastExpressionsMatch(@NotNull PsiTypeCastExpression typeCastExpression1, @NotNull PsiTypeCastExpression typeCastExpression2) {
    final PsiTypeElement typeElement1 = typeCastExpression1.getCastType();
    final PsiTypeElement typeElement2 = typeCastExpression2.getCastType();
    if (!typeElementsAreEquivalent(typeElement1, typeElement2).isExactMatch()) {
      return EXACT_MISMATCH;
    }
    final PsiExpression operand1 = typeCastExpression1.getOperand();
    final PsiExpression operand2 = typeCastExpression2.getOperand();
    if (operand1 instanceof PsiFunctionalExpression || operand2 instanceof PsiFunctionalExpression) {
      return EXACT_MISMATCH;
    }
    return expressionsMatch(operand1, operand2).partialIfExactMismatch(operand1, operand2);
  }

  protected Match arrayAccessExpressionsMatch(@NotNull PsiArrayAccessExpression arrayAccessExpression1, @NotNull PsiArrayAccessExpression arrayAccessExpression2) {
    final PsiExpression arrayExpression2 =
      arrayAccessExpression1.getArrayExpression();
    final PsiExpression arrayExpression1 =
      arrayAccessExpression2.getArrayExpression();
    final PsiExpression indexExpression2 =
      arrayAccessExpression1.getIndexExpression();
    final PsiExpression indexExpression1 =
      arrayAccessExpression2.getIndexExpression();
    final Match arrayExpressionEq = expressionsMatch(arrayExpression2, arrayExpression1);
    if (arrayExpressionEq != EXACT_MATCH) {
      return EXACT_MISMATCH;
    }
    return expressionsMatch(indexExpression1, indexExpression2).partialIfExactMismatch(indexExpression1, indexExpression2);
  }

  protected Match prefixExpressionsMatch(@NotNull PsiPrefixExpression prefixExpression1, @NotNull PsiPrefixExpression prefixExpression2) {
    final IElementType tokenType1 = prefixExpression1.getOperationTokenType();
    if (!tokenType1.equals(prefixExpression2.getOperationTokenType())) {
      return EXACT_MISMATCH;
    }
    final PsiExpression operand1 = prefixExpression1.getOperand();
    final PsiExpression operand2 = prefixExpression2.getOperand();
    return expressionsMatch(operand1, operand2);
  }

  protected Match postfixExpressionsMatch(@NotNull PsiPostfixExpression postfixExpression1, @NotNull PsiPostfixExpression postfixExpression2) {
    final IElementType tokenType1 = postfixExpression1.getOperationTokenType();
    if (!tokenType1.equals(postfixExpression2.getOperationTokenType())) {
      return EXACT_MISMATCH;
    }
    final PsiExpression operand1 = postfixExpression1.getOperand();
    final PsiExpression operand2 = postfixExpression2.getOperand();
    return expressionsMatch(operand1, operand2);
  }

  protected Match polyadicExpressionsMatch(@NotNull PsiPolyadicExpression polyadicExpression1, @NotNull PsiPolyadicExpression polyadicExpression2) {
    final IElementType tokenType1 = polyadicExpression1.getOperationTokenType();
    final IElementType tokenType2 = polyadicExpression2.getOperationTokenType();
    if (!tokenType1.equals(tokenType2)) {
      return EXACT_MISMATCH;
    }
    final PsiExpression[] operands1 = polyadicExpression1.getOperands();
    final PsiExpression[] operands2 = polyadicExpression2.getOperands();
    return expressionsAreEquivalent(operands1, operands2);
  }

  protected Match binaryExpressionsMatch(@NotNull PsiBinaryExpression binaryExpression1, @NotNull PsiBinaryExpression binaryExpression2) {
    final IElementType tokenType1 = binaryExpression1.getOperationTokenType();
    final IElementType tokenType2 = binaryExpression2.getOperationTokenType();
    final PsiExpression left1 = binaryExpression1.getLOperand();
    final PsiExpression left2 = binaryExpression2.getLOperand();
    final PsiExpression right1 = binaryExpression1.getROperand();
    final PsiExpression right2 = binaryExpression2.getROperand();
    if (!tokenType1.equals(tokenType2)) {
      // process matches like "a < b" and "b > a"
      DfaRelationValue.RelationType rel1 = DfaRelationValue.RelationType.fromElementType(tokenType1);
      DfaRelationValue.RelationType rel2 = DfaRelationValue.RelationType.fromElementType(tokenType2);
      if(rel1 != null && rel2 != null && rel1.getFlipped() == rel2) {
        return expressionsAreEquivalent(new PsiExpression[] {left1, right1}, new PsiExpression[] {right2, left2});
      }
      return EXACT_MISMATCH;
    }
    return expressionsAreEquivalent(new PsiExpression[] {left1, right1}, new PsiExpression[] {left2, right2});
  }

  protected Match assignmentExpressionsMatch(@NotNull PsiAssignmentExpression assignmentExpression1, @NotNull PsiAssignmentExpression assignmentExpression2) {
    final IElementType tokenType1 = assignmentExpression1.getOperationTokenType();
    if (!tokenType1.equals(assignmentExpression2.getOperationTokenType())) {
      return EXACT_MISMATCH;
    }
    final PsiExpression lhs1 = assignmentExpression1.getLExpression();
    final PsiExpression lhs2 = assignmentExpression2.getLExpression();
    final PsiExpression rhs1 = assignmentExpression1.getRExpression();
    final PsiExpression rhs2 = assignmentExpression2.getRExpression();
    final Match leftEq = expressionsMatch(lhs1, lhs2);
    final Match rightEq = expressionsMatch(rhs1, rhs2);
    return getComplexElementDecision(leftEq, rightEq, lhs1, lhs2, rhs1, rhs2);
  }

  protected Match conditionalExpressionsMatch(@NotNull PsiConditionalExpression conditionalExpression1, @NotNull PsiConditionalExpression conditionalExpression2) {
    final PsiExpression condition1 = conditionalExpression1.getCondition();
    final PsiExpression condition2 = conditionalExpression2.getCondition();
    final PsiExpression thenExpression1 =
      conditionalExpression1.getThenExpression();
    final PsiExpression thenExpression2 =
      conditionalExpression2.getThenExpression();
    final PsiExpression elseExpression1 =
      conditionalExpression1.getElseExpression();
    final PsiExpression elseExpression2 =
      conditionalExpression2.getElseExpression();
    if (expressionsMatch(condition1, condition2) == EXACT_MATCH &&
        expressionsMatch(thenExpression1, thenExpression2) == EXACT_MATCH &&
        expressionsMatch(elseExpression1, elseExpression2) == EXACT_MATCH) {
      return EXACT_MATCH;
    }
    return EXACT_MISMATCH;
  }

  protected Match expressionsAreEquivalent(@Nullable PsiExpression[] expressions1, @Nullable PsiExpression[] expressions2) {
    if (expressions1 == null && expressions2 == null) {
      return EXACT_MATCH;
    }
    if (expressions1 == null || expressions2 == null) {
      return EXACT_MISMATCH;
    }
    if (expressions1.length != expressions2.length) {
      return EXACT_MISMATCH;
    }

    Match incompleteMatch = null;
    for (int i = 0; i < expressions1.length; i++) {
      final Match match = expressionsMatch(expressions1[i], expressions2[i]);
      if (incompleteMatch == null && match.isPartialMatch()) {
        incompleteMatch = match;
      }
      else if (!match.isExactMatch()) {
        if (incompleteMatch != null) {
          return EXACT_MISMATCH;
        }
        incompleteMatch = match.partialIfExactMismatch(expressions1[i], expressions2[i]);
      }
    }
    return incompleteMatch == null ? EXACT_MATCH : incompleteMatch;
  }

  @NotNull
  private static Match getComplexElementDecision(Match equivalence1,
                                                 Match equivalence2,
                                                 PsiElement left1,
                                                 PsiElement right1,
                                                 PsiElement left2,
                                                 PsiElement right2) {
    if (equivalence2 == EXACT_MATCH) {
      if (equivalence1 == EXACT_MATCH) {
        return EXACT_MATCH;
      }
      else if (equivalence1 == EXACT_MISMATCH) {
        return new Match(left1, right1);
      }
    }
    else if (equivalence2 == EXACT_MISMATCH) {
      if (equivalence1 == EXACT_MISMATCH) {
        return EXACT_MISMATCH;
      }
      else if (equivalence1 == EXACT_MATCH) {
        return new Match(left2, right2);
      }
    }
    return EXACT_MISMATCH;
  }
}