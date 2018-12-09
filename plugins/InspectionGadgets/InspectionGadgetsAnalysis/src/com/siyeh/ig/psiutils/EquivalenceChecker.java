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
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.*;

/**
 * This equivalence checker will consider references to variables, declared inside the element checked for equivalence,
 * NOT equivalent.
 * @see TrackingEquivalenceChecker  which also tracks declaration equivalence, to accurately check the
 * equivalence of reference expressions.
 */
public class EquivalenceChecker {
  protected static final Match EXACT_MATCH = new Match(true);
  protected static final Match EXACT_MISMATCH = new Match(false);
  private static final EquivalenceChecker ourCanonicalPsiEquivalence = new EquivalenceChecker();
  private static final Comparator<PsiMember> MEMBER_COMPARATOR =
    comparing(PsiMember::getName, nullsFirst(naturalOrder())).thenComparing(PsiMember::getText);
  private static final Comparator<PsiExpression> EXPRESSION_COMPARATOR =
    comparing(ParenthesesUtils::stripParentheses, nullsFirst(comparing(PsiExpression::getText)));

  protected EquivalenceChecker() {}

  /**
   * Returns a shareable EquivalenceChecker instance that does not track declaration equivalence.
   * @return a shareable EquivalenceChecker instance
   */
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
    if (statement1 == null || statement2 == null) {
      return Match.exact(statement1 == statement2);
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
      return switchBlocksMatch((PsiSwitchStatement)statement1, (PsiSwitchStatement)statement2);
    }
    if (statement1 instanceof PsiSwitchLabelStatementBase && statement2 instanceof PsiSwitchLabelStatementBase) {
      return switchLabelStatementsMatch((PsiSwitchLabelStatementBase)statement1, (PsiSwitchLabelStatementBase)statement2);
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
    final PsiElement[] elements2 = statement2.getDeclaredElements();
    if (elements1.length != elements2.length) {
      return EXACT_MISMATCH;
    }
    for (int i = 0; i < elements1.length; i++) {
      final PsiElement element1 = elements1[i];
      final PsiElement element2 = elements2[i];
      if (!(element1 instanceof PsiLocalVariable) ||
          !(element2 instanceof PsiLocalVariable) ||
          !localVariablesAreEquivalent((PsiLocalVariable)element1, (PsiLocalVariable)element2).isExactMatch()) {
        return EXACT_MISMATCH;
      }
    }
    return EXACT_MATCH;
  }

  protected Match localVariablesAreEquivalent(@NotNull PsiLocalVariable localVariable1,
                                              @NotNull PsiLocalVariable localVariable2) {
    return variablesAreEquivalent(localVariable1, localVariable2);
  }

  protected Match variablesAreEquivalent(@NotNull PsiVariable variable1, @NotNull PsiVariable variable2) {
    if (!variableSignatureMatch(variable1, variable2)) {
      return EXACT_MISMATCH;
    }
    PsiExpression initializer1 = variable1.getInitializer();
    PsiExpression initializer2 = variable2.getInitializer();
    return expressionsMatch(initializer1, initializer2).partialIfExactMismatch(initializer1, initializer2);
  }

  private boolean variableSignatureMatch(@NotNull PsiVariable variable1, @NotNull PsiVariable variable2) {
    PsiType type1 = variable1.getType();
    PsiType type2 = variable2.getType();
    if (!typesAreEquivalent(type1, type2)) {
      return false;
    }
    PsiModifierList modifierList1 = variable1.getModifierList();
    PsiModifierList modifierList2 = variable2.getModifierList();
    if (modifierList1 == null || modifierList2 == null) {
      return modifierList1 == modifierList2;
    }
    if (!modifierListsAreEquivalent(modifierList1, modifierList2)) {
      return false;
    }
    markDeclarationsAsEquivalent(variable1, variable2);
    return true;
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
    if (resourceList1 == null || resourceList2 == null) {
      return Match.exact(resourceList1 == resourceList2);
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
        if (!variablesAreEquivalent((PsiLocalVariable)resource1, (PsiLocalVariable)resource2).isExactMatch()) {
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
    final PsiParameter[] catchParameters1 = statement1.getCatchBlockParameters();
    final PsiParameter[] catchParameters2 = statement2.getCatchBlockParameters();
    if (catchParameters1.length != catchParameters2.length) {
      return EXACT_MISMATCH;
    }
    for (int i = 0; i < catchParameters2.length; i++) {
      if (!variablesAreEquivalent(catchParameters2[i], catchParameters1[i]).isExactMatch()) {
        return EXACT_MISMATCH;
      }
    }
    return EXACT_MATCH;
  }

  public boolean typesAreEquivalent(@Nullable PsiType type1, @Nullable PsiType type2) {
    if (type1 == null || type2 == null) {
      return type1 == type2;
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
    final String name2 = parameter2.getName();
    if (name1 == null || name2 == null || !name1.equals(name2)) {
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

  protected Match switchBlocksMatch(@NotNull PsiSwitchBlock switchBlock1, @NotNull PsiSwitchBlock switchBlock2) {
    final PsiCodeBlock body1 = switchBlock1.getBody();
    final PsiCodeBlock body2 = switchBlock2.getBody();
    if (!codeBlocksAreEquivalent(body1, body2)) {
      return EXACT_MISMATCH;
    }
    final PsiExpression switchExpression1 = switchBlock1.getExpression();
    final PsiExpression switchExpression2 = switchBlock2.getExpression();
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
    final PsiExpression expression1 = statement1.getExpression();
    final PsiExpression expression2 = statement2.getExpression();
    if (expression1 == null || expression2 == null) {
      return Match.exact(expression1 == expression2);
    }
    return expressionsMatch(expression1, expression2);
  }

  protected Match continueStatementsMatch(@NotNull PsiContinueStatement statement1, @NotNull PsiContinueStatement statement2) {
    final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
    final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
    if (identifier1 == null || identifier2 == null) {
      return Match.exact(identifier1 == identifier2);
    }
    final String text1 = identifier1.getText();
    final String text2 = identifier2.getText();
    return Match.exact(text1.equals(text2));
  }

  protected Match switchLabelStatementsMatch(@NotNull PsiSwitchLabelStatementBase statement1,
                                             @NotNull PsiSwitchLabelStatementBase statement2) {
    if (statement1.isDefaultCase() != statement2.isDefaultCase()) {
      return EXACT_MISMATCH;
    }
    final boolean rule1 = statement1 instanceof PsiSwitchLabeledRuleStatement;
    final boolean rule2 = statement2 instanceof PsiSwitchLabeledRuleStatement;
    if (rule1 && rule2) {
      final PsiSwitchLabeledRuleStatement switchLabeledRuleStatement1 = (PsiSwitchLabeledRuleStatement)statement1;
      final PsiSwitchLabeledRuleStatement switchLabeledRuleStatement2 = (PsiSwitchLabeledRuleStatement)statement2;
      if (!statementsAreEquivalent(switchLabeledRuleStatement1.getBody(), switchLabeledRuleStatement2.getBody())) {
        return EXACT_MISMATCH;
      }
    }
    else if (rule1 || rule2) {
      return EXACT_MISMATCH;
    }
    final PsiExpressionList caseValues1 = statement1.getCaseValues();
    final PsiExpressionList caseValues2 = statement2.getCaseValues();
    if (caseValues1 == null || caseValues2 == null) {
      return Match.exact(caseValues1 == caseValues2);
    }
    return expressionsAreEquivalent(caseValues1.getExpressions(), caseValues2.getExpressions(), true);
  }

  protected Match labeledStatementsMatch(@NotNull PsiLabeledStatement statement1, @NotNull PsiLabeledStatement statement2) {
    return Match.exact(statement1.getName().equals(statement2.getName()));
  }

  public boolean codeBlocksAreEquivalent(@Nullable PsiCodeBlock block1, @Nullable PsiCodeBlock block2) {
    return codeBlocksMatch(block1, block2).isExactMatch();
  }

  protected Match codeBlocksMatch(@Nullable PsiCodeBlock block1, @Nullable PsiCodeBlock block2) {
    if (block1 == null || block2 == null) {
      return Match.exact(block1 == block2);
    }
    final List<PsiStatement> statements1 = collectStatements(block1, new SmartList<>());
    final List<PsiStatement> statements2 = collectStatements(block2, new SmartList<>());
    final int size = statements1.size();
    if (size != statements2.size()) {
      return EXACT_MISMATCH;
    }
    for (int i = 0; i < size; i++) {
      if (!statementsMatch(statements2.get(i), statements1.get(i)).isExactMatch()) {
        return EXACT_MISMATCH;
      }
    }
    return EXACT_MATCH;
  }

  private static List<PsiStatement> collectStatements(PsiCodeBlock codeBlock, List<PsiStatement> out) {
    for (PsiStatement statement : codeBlock.getStatements()) {
      if (statement instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
        collectStatements(blockStatement.getCodeBlock(), out);
      }
      else if (!(statement instanceof PsiEmptyStatement)) {
        out.add(statement);
      }
    }
    return out;
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
    final PsiExpression[] expressions1 = statement1.getExpressionList().getExpressions();
    final PsiExpression[] expressions2 = statement2.getExpressionList().getExpressions();
    return expressionsAreEquivalent(expressions1, expressions2, false);
  }

  public boolean expressionsAreEquivalent(@Nullable PsiExpression expression1, @Nullable PsiExpression expression2) {
    return expressionsMatch(expression1, expression2).isExactMatch();
  }

  public Match expressionsMatch(@Nullable PsiExpression expression1, @Nullable PsiExpression expression2) {
    if (expression1 == expression2) {
      return EXACT_MATCH;
    }
    expression1 = ParenthesesUtils.stripParentheses(expression1);
    expression2 = ParenthesesUtils.stripParentheses(expression2);
    if (expression1 == null || expression2 == null) {
      return Match.exact(expression1 == expression2);
    }
    if (expression1.getClass() != expression2.getClass()) {
      return EXACT_MISMATCH;
    }
    if (expression1 instanceof PsiThisExpression) {
      return thisExpressionsMatch((PsiThisExpression)expression1, (PsiThisExpression)expression2);
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
    if (expression1 instanceof PsiUnaryExpression) {
      return unaryExpressionsMatch((PsiUnaryExpression)expression1, (PsiUnaryExpression)expression2);
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
    if (expression1 instanceof PsiSwitchExpression) {
      return switchBlocksMatch((PsiSwitchExpression)expression1, (PsiSwitchExpression)expression2);
    }
    return EXACT_MISMATCH;
  }

  @NotNull
  private Match thisExpressionsMatch(@NotNull PsiThisExpression thisExpression1, @NotNull PsiThisExpression thisExpression2) {
    final PsiJavaCodeReferenceElement qualifier1 = thisExpression1.getQualifier();
    final PsiJavaCodeReferenceElement qualifier2 = thisExpression2.getQualifier();
    if (qualifier1 != null && qualifier2 != null) {
      return javaCodeReferenceElementsMatch(qualifier1, qualifier2);
    }
    else if (qualifier1 != qualifier2){
      return EXACT_MISMATCH;
    }
    final PsiClass containingClass1 = PsiTreeUtil.getParentOfType(thisExpression1, PsiClass.class);
    final PsiClass containingClass2 = PsiTreeUtil.getParentOfType(thisExpression2, PsiClass.class);
    return Match.exact(containingClass1 == containingClass2);
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
      if (!variablesAreEquivalent(parameters1[i], parameters2[i]).isExactMatch()) {
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
    if (PsiType.NULL.equals(expression1.getType()) && PsiType.NULL.equals(expression2.getType())) {
      return EXACT_MATCH;
    }
    final Object value1 = expression1.getValue();
    final Object value2 = expression2.getValue();
    return (value1 == null || value2 == null)
           ? EXACT_MISMATCH // broken code
           : Match.exact(value1.equals(value2));
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
      if (element2 == null || !equivalentDeclarations(element1, element2) && !element1.equals(element2)) {
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
      Match match = expressionsMatch(qualifier1, qualifier2);
      if (match.isExactMismatch()) {
        return new Match(qualifier1, qualifier2);
      }
      return match;
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
    if (typeElement1 == null || typeElement2 == null) {
      return Match.exact(typeElement1 == typeElement2);
    }
    final PsiType type1 = typeElement1.getType();
    final PsiType type2 = typeElement2.getType();
    return Match.exact(typesAreEquivalent(type1, type2));
  }

  protected Match methodCallExpressionsMatch(@NotNull PsiMethodCallExpression methodCallExpression1,
                                             @NotNull PsiMethodCallExpression methodCallExpression2) {
    final PsiReferenceExpression methodExpression1 = methodCallExpression1.getMethodExpression();
    final PsiReferenceExpression methodExpression2 = methodCallExpression2.getMethodExpression();
    Match match = expressionsMatch(methodExpression1, methodExpression2);
    if (match.isExactMismatch()) {
      return EXACT_MISMATCH;
    }
    final PsiExpression[] args1 = methodCallExpression1.getArgumentList().getExpressions();
    final PsiExpression[] args2 = methodCallExpression2.getArgumentList().getExpressions();
    match = match.combine(expressionsAreEquivalent(args1, args2, false));

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
    final PsiJavaCodeReferenceElement classReference1 = newExpression1.getClassReference();
    final PsiJavaCodeReferenceElement classReference2 = newExpression2.getClassReference();
    if (classReference1 != null && classReference2 != null) {
      if (javaCodeReferenceElementsMatch(classReference1, classReference2) == EXACT_MISMATCH) {
        return EXACT_MISMATCH;
      }
    }
    else if (classReference1 != classReference2) {
      return EXACT_MISMATCH;
    }
    final PsiExpression[] arrayDimensions1 = newExpression1.getArrayDimensions();
    final PsiExpression[] arrayDimensions2 = newExpression2.getArrayDimensions();
    if (!expressionsAreEquivalent(arrayDimensions1, arrayDimensions2, false).isExactMatch()) {
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
    PsiAnonymousClass anonymousClass1 = newExpression1.getAnonymousClass();
    PsiAnonymousClass anonymousClass2 = newExpression2.getAnonymousClass();
    if (anonymousClass1 != null || anonymousClass2 != null) {
      if (anonymousClass1 != null && anonymousClass2 != null) {
        return classesMatch(anonymousClass1, anonymousClass2);
      }
      return EXACT_MISMATCH;
    }
    return expressionsAreEquivalent(args1, args2, false);
  }

  private Match classesMatch(PsiAnonymousClass class1, PsiAnonymousClass class2) {
    PsiJavaCodeReferenceElement baseClass1 = class1.getBaseClassReference();
    PsiJavaCodeReferenceElement baseClass2 = class2.getBaseClassReference();
    Match match = javaCodeReferenceElementsMatch(baseClass1, baseClass2);
    if (!match.isExactMatch()) return EXACT_MISMATCH;
    List<PsiMember> children1 = PsiTreeUtil.getChildrenOfTypeAsList(class1, PsiMember.class);
    List<PsiMember> children2 = PsiTreeUtil.getChildrenOfTypeAsList(class2, PsiMember.class);
    int size = children1.size();
    if (size != children2.size()) return EXACT_MISMATCH;
    Collections.sort(children1, MEMBER_COMPARATOR);
    Collections.sort(children2, MEMBER_COMPARATOR);
    for (int i = 0; i < size; i++) {
      // first pass checks only signatures for accurate reference tracking
      PsiElement child1 = children1.get(i);
      PsiElement child2 = children2.get(i);
      if (child1 instanceof PsiMethod && child2 instanceof PsiMethod) {
        if (!methodSignaturesMatch((PsiMethod)child1, (PsiMethod)child2)) {
          return EXACT_MISMATCH;
        }
      } else if (child1 instanceof PsiField && child2 instanceof PsiField) {
        if (!variableSignatureMatch((PsiField)child1, (PsiField)child2)) {
          return EXACT_MISMATCH;
        }
      }
    }
    for (int i = 0; i < size; i++) {
      PsiElement child1 = children1.get(i);
      PsiElement child2 = children2.get(i);
      if (child1 instanceof PsiMethod && child2 instanceof PsiMethod) {
        // method signature already checked
        if (!codeBlocksAreEquivalent(((PsiMethod)child1).getBody(), ((PsiMethod)child2).getBody())) return EXACT_MISMATCH;
      } else if (child1 instanceof PsiField && child2 instanceof PsiField) {
        // field signature already checked
        if (!expressionsAreEquivalent(((PsiField)child1).getInitializer(), ((PsiField)child2).getInitializer())) return EXACT_MISMATCH;
      } else if (child1 instanceof PsiClassInitializer && child2 instanceof PsiClassInitializer) {
        if (!classInitializersMatch((PsiClassInitializer)child1, (PsiClassInitializer)child2).isExactMatch()) return EXACT_MISMATCH;
      } else if (!PsiEquivalenceUtil.areElementsEquivalent(child1, child2)) {
        return EXACT_MISMATCH;
      }
    }
    return EXACT_MATCH;
  }

  private Match classInitializersMatch(PsiClassInitializer classInitializer1, PsiClassInitializer classInitializer2) {
    if (!modifierListsAreEquivalent(classInitializer1.getModifierList(), classInitializer2.getModifierList())) {
      return EXACT_MISMATCH;
    }
    return codeBlocksMatch(classInitializer1.getBody(), classInitializer2.getBody());
  }

  private boolean methodSignaturesMatch(PsiMethod method1, PsiMethod method2) {
    if (!method1.getName().equals(method2.getName()) || !typesAreEquivalent(method1.getReturnType(), method2.getReturnType())) {
      return false;
    }
    PsiParameter[] parameters1 = method1.getParameterList().getParameters();
    PsiParameter[] parameters2 = method2.getParameterList().getParameters();
    if (parameters1.length != parameters2.length) {
      return false;
    }
    for (int j = 0; j < parameters1.length; j++) {
      if (!variableSignatureMatch(parameters1[j], parameters2[j])) {
        return false;
      }
    }
    PsiClassType[] thrownTypes1 = method1.getThrowsList().getReferencedTypes();
    PsiClassType[] thrownTypes2 = method2.getThrowsList().getReferencedTypes();
    if (thrownTypes1.length != thrownTypes2.length) {
      return false;
    }
    for (int i = 0; i < thrownTypes1.length; i++) {
      if (!typesAreEquivalent(thrownTypes1[i], thrownTypes2[i])) {
        return false;
      }
    }
    markDeclarationsAsEquivalent(method1, method2);
    return true;
  }

  private Match javaCodeReferenceElementsMatch(@NotNull PsiJavaCodeReferenceElement classReference1,
                                               @NotNull PsiJavaCodeReferenceElement classReference2) {
    final PsiType[] parameters1 = classReference1.getTypeParameters();
    final PsiType[] parameters2 = classReference2.getTypeParameters();
    if (parameters1.length != parameters2.length) {
      return EXACT_MISMATCH;
    }
    for (int i = 0; i < parameters1.length; i++) {
      if (!typesAreEquivalent(parameters1[i], parameters2[i])) {
        return EXACT_MISMATCH;
      }
    }
    final PsiElement target1 = classReference1.resolve();
    final PsiElement target2 = classReference2.resolve();
    return (target1 == null && target2 == null)
           ? Match.exact(classReference1.getText().equals(classReference2.getText()))
           : Match.exact(target1 == target2);
  }

  protected Match arrayInitializerExpressionsMatch(@NotNull PsiArrayInitializerExpression arrayInitializerExpression1,
                                                   @NotNull PsiArrayInitializerExpression arrayInitializerExpression2) {
    final PsiExpression[] initializers1 = arrayInitializerExpression1.getInitializers();
    final PsiExpression[] initializers2 = arrayInitializerExpression2.getInitializers();
    return expressionsAreEquivalent(initializers1, initializers2, false);
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

  protected Match unaryExpressionsMatch(@NotNull PsiUnaryExpression unaryExpression1, @NotNull PsiUnaryExpression unaryExpression2) {
    final IElementType tokenType1 = unaryExpression1.getOperationTokenType();
    if (!tokenType1.equals(unaryExpression2.getOperationTokenType())) {
      return EXACT_MISMATCH;
    }
    final PsiExpression operand1 = unaryExpression1.getOperand();
    final PsiExpression operand2 = unaryExpression2.getOperand();
    return expressionsMatch(operand1, operand2);
  }

  protected Match polyadicExpressionsMatch(@NotNull PsiPolyadicExpression polyadicExpression1,
                                           @NotNull PsiPolyadicExpression polyadicExpression2) {
    if (!polyadicExpression1.getOperationTokenType().equals(polyadicExpression2.getOperationTokenType())) {
      return EXACT_MISMATCH;
    }
    return expressionsAreEquivalent(polyadicExpression1.getOperands(), polyadicExpression2.getOperands(), false);
  }

  protected Match binaryExpressionsMatch(@NotNull PsiBinaryExpression binaryExpression1, @NotNull PsiBinaryExpression binaryExpression2) {
    final IElementType tokenType1 = binaryExpression1.getOperationTokenType();
    final IElementType tokenType2 = binaryExpression2.getOperationTokenType();
    final PsiExpression left1 = binaryExpression1.getLOperand();
    final PsiExpression left2 = binaryExpression2.getLOperand();
    final PsiExpression right1 = binaryExpression1.getROperand();
    final PsiExpression right2 = binaryExpression2.getROperand();
    if (right1 == null || right2 == null) {
      return Match.exact(right1 == right2);
    }
    if (!tokenType1.equals(tokenType2)) {
      // process matches like "a < b" and "b > a"
      final DfaRelationValue.RelationType rel1 = DfaRelationValue.RelationType.fromElementType(tokenType1);
      final DfaRelationValue.RelationType rel2 = DfaRelationValue.RelationType.fromElementType(tokenType2);
      if(rel1 != null && rel2 != null && rel1.getFlipped() == rel2) {
        return expressionsAreEquivalent(new PsiExpression[] {left1, right1}, new PsiExpression[] {right2, left2}, false);
      }
      return EXACT_MISMATCH;
    }
    return expressionsAreEquivalent(new PsiExpression[] {left1, right1}, new PsiExpression[] {left2, right2},
                                    ParenthesesUtils.isCommutativeOperation(binaryExpression1));
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
    final PsiExpression thenExpression1 = conditionalExpression1.getThenExpression();
    final PsiExpression thenExpression2 = conditionalExpression2.getThenExpression();
    final PsiExpression elseExpression1 = conditionalExpression1.getElseExpression();
    final PsiExpression elseExpression2 = conditionalExpression2.getElseExpression();
    if (expressionsMatch(condition1, condition2) == EXACT_MATCH &&
        expressionsMatch(thenExpression1, thenExpression2) == EXACT_MATCH &&
        expressionsMatch(elseExpression1, elseExpression2) == EXACT_MATCH) {
      return EXACT_MATCH;
    }
    return EXACT_MISMATCH;
  }

  protected Match expressionsAreEquivalent(@Nullable PsiExpression[] expressions1, @Nullable PsiExpression[] expressions2, boolean inAnyOrder) {
    if (expressions1 == null || expressions2 == null) {
      return Match.exact(expressions1 == expressions2);
    }
    if (expressions1.length != expressions2.length) {
      return EXACT_MISMATCH;
    }
    if (inAnyOrder) {
      Arrays.sort(expressions1, EXPRESSION_COMPARATOR);
      Arrays.sort(expressions2, EXPRESSION_COMPARATOR);
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

  private static boolean modifierListsAreEquivalent(PsiModifierList modifierList1, PsiModifierList modifierList2) {
    for (String modifier : PsiModifier.MODIFIERS) {
      if (PsiModifier.FINAL.equals(modifier)) continue; // final does not change the semantics of the code.
      if (modifierList1.hasModifierProperty(modifier) != modifierList2.hasModifierProperty(modifier)) {
        return false;
      }
    }
    return AnnotationUtil.equal(modifierList1.getAnnotations(), modifierList2.getAnnotations());
  }

  protected void markDeclarationsAsEquivalent(PsiElement element1, PsiElement element2) {}

  protected boolean equivalentDeclarations(PsiElement element1, PsiElement element2) {
    return false;
  }
}