/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EquivalenceChecker {

  private EquivalenceChecker() {}

  public static boolean statementsAreEquivalent(@Nullable PsiStatement statement1, @Nullable PsiStatement statement2) {
    statement1 = ControlFlowUtils.stripBraces(statement1);
    statement2 = ControlFlowUtils.stripBraces(statement2);
    if (statement1 == null) {
      return statement2 == null;
    } else if (statement2 == null) {
      return false;
    }
    if (statement1.getClass() != statement2.getClass()) {
        return false;
    }
    if (statement1 instanceof PsiAssertStatement) {
      return assertStatementsAreEquivalent((PsiAssertStatement)statement1, (PsiAssertStatement)statement2);
    }
    if (statement1 instanceof PsiBlockStatement) {
      return blockStatementsAreEquivalent((PsiBlockStatement)statement1, (PsiBlockStatement)statement2);
    }
    if (statement1 instanceof PsiBreakStatement) {
      return breakStatementsAreEquivalent((PsiBreakStatement)statement1, (PsiBreakStatement)statement2);
    }
    if (statement1 instanceof PsiContinueStatement) {
      return continueStatementsAreEquivalent((PsiContinueStatement)statement1, (PsiContinueStatement)statement2);
    }
    if (statement1 instanceof PsiDeclarationStatement) {
      return declarationStatementsAreEquivalent((PsiDeclarationStatement)statement1, (PsiDeclarationStatement)statement2);
    }
    if (statement1 instanceof PsiDoWhileStatement) {
      return doWhileStatementsAreEquivalent((PsiDoWhileStatement)statement1, (PsiDoWhileStatement)statement2);
    }
    if (statement1 instanceof PsiEmptyStatement) {
      return true;
    }
    if (statement1 instanceof PsiExpressionListStatement) {
      return expressionListStatementsAreEquivalent((PsiExpressionListStatement)statement1, (PsiExpressionListStatement)statement2);
    }
    if (statement1 instanceof PsiExpressionStatement) {
      return expressionStatementsAreEquivalent((PsiExpressionStatement)statement1, (PsiExpressionStatement)statement2);
    }
    if (statement1 instanceof PsiForStatement) {
      return forStatementsAreEquivalent((PsiForStatement)statement1, (PsiForStatement)statement2);
    }
    if (statement1 instanceof PsiForeachStatement) {
      return forEachStatementsAreEquivalent((PsiForeachStatement)statement1, (PsiForeachStatement)statement2);
    }
    if (statement1 instanceof PsiIfStatement) {
      return ifStatementsAreEquivalent((PsiIfStatement)statement1, (PsiIfStatement)statement2);
    }
    if (statement1 instanceof PsiLabeledStatement) {
      return labeledStatementsAreEquivalent((PsiLabeledStatement)statement1, (PsiLabeledStatement)statement2);
    }
    if (statement1 instanceof PsiReturnStatement) {
      return returnStatementsAreEquivalent((PsiReturnStatement)statement1, (PsiReturnStatement)statement2);
    }
    if (statement1 instanceof PsiSwitchStatement) {
      return switchStatementsAreEquivalent((PsiSwitchStatement)statement1, (PsiSwitchStatement)statement2);
    }
    if (statement1 instanceof PsiSwitchLabelStatement) {
      return switchLabelStatementsAreEquivalent((PsiSwitchLabelStatement)statement1, (PsiSwitchLabelStatement)statement2);
    }
    if (statement1 instanceof PsiSynchronizedStatement) {
      return synchronizedStatementsAreEquivalent((PsiSynchronizedStatement)statement1, (PsiSynchronizedStatement)statement2);
    }
    if (statement1 instanceof PsiThrowStatement) {
      return throwStatementsAreEquivalent((PsiThrowStatement)statement1, (PsiThrowStatement)statement2);
    }
    if (statement1 instanceof PsiTryStatement) {
      return tryStatementsAreEquivalent((PsiTryStatement)statement1, (PsiTryStatement)statement2);
    }
    if (statement1 instanceof PsiWhileStatement) {
      return whileStatementsAreEquivalent((PsiWhileStatement)statement1, (PsiWhileStatement)statement2);
    }
    final String text1 = statement1.getText();
    final String text2 = statement2.getText();
    return text1.equals(text2);
  }

  private static boolean declarationStatementsAreEquivalent(
    @NotNull PsiDeclarationStatement statement1,
    @NotNull PsiDeclarationStatement statement2) {
    final PsiElement[] elements1 = statement1.getDeclaredElements();
    final List<PsiLocalVariable> vars1 =
      new ArrayList<PsiLocalVariable>(elements1.length);
    for (PsiElement anElement : elements1) {
      if (anElement instanceof PsiLocalVariable) {
        vars1.add((PsiLocalVariable)anElement);
      }
    }
    final PsiElement[] elements2 = statement2.getDeclaredElements();
    final List<PsiLocalVariable> vars2 =
      new ArrayList<PsiLocalVariable>(elements2.length);
    for (PsiElement anElement : elements2) {
      if (anElement instanceof PsiLocalVariable) {
        vars2.add((PsiLocalVariable)anElement);
      }
    }
    final int size = vars1.size();
    if (size != vars2.size()) {
      return false;
    }
    for (int i = 0; i < size; i++) {
      final PsiLocalVariable var1 = vars1.get(i);
      final PsiLocalVariable var2 = vars2.get(i);
      if (!localVariablesAreEquivalent(var1, var2)) {
        return false;
      }
    }
    return true;
  }

  private static boolean localVariablesAreEquivalent(
    @NotNull PsiLocalVariable localVariable1,
    @NotNull PsiLocalVariable localVariable2) {
    final PsiType type1 = localVariable1.getType();
    final PsiType type2 = localVariable2.getType();
    if (!typesAreEquivalent(type1, type2)) {
      return false;
    }
    final String name1 = localVariable1.getName();
    final String name2 = localVariable2.getName();
    if (name1 == null) {
      return name2 == null;
    }
    if (!name1.equals(name2)) {
      return false;
    }
    final PsiExpression initializer1 = localVariable1.getInitializer();
    final PsiExpression initializer2 = localVariable2.getInitializer();
    return expressionsAreEquivalent(initializer1, initializer2);
  }

  private static boolean tryStatementsAreEquivalent(@NotNull PsiTryStatement statement1, @NotNull PsiTryStatement statement2) {
    final PsiCodeBlock tryBlock1 = statement1.getTryBlock();
    final PsiCodeBlock tryBlock2 = statement2.getTryBlock();
    if (!codeBlocksAreEquivalent(tryBlock1, tryBlock2)) {
      return false;
    }
    final PsiCodeBlock finallyBlock1 = statement1.getFinallyBlock();
    final PsiCodeBlock finallyBlock2 = statement2.getFinallyBlock();
    if (!codeBlocksAreEquivalent(finallyBlock1, finallyBlock2)) {
      return false;
    }
    final PsiCodeBlock[] catchBlocks1 = statement1.getCatchBlocks();
    final PsiCodeBlock[] catchBlocks2 = statement2.getCatchBlocks();
    if (catchBlocks1.length != catchBlocks2.length) {
      return false;
    }
    for (int i = 0; i < catchBlocks2.length; i++) {
      if (!codeBlocksAreEquivalent(catchBlocks1[i], catchBlocks2[i])) {
        return false;
      }
    }
    final PsiResourceList resourceList1 = statement1.getResourceList();
    final PsiResourceList resourceList2 = statement2.getResourceList();
    if (resourceList1 != null) {
      if (resourceList2 == null) {
        return false;
      }
      if (resourceList1.getResourceVariablesCount() != resourceList2.getResourceVariablesCount()) {
        return false;
      }
      final List<PsiResourceListElement> resources1 = PsiTreeUtil.getChildrenOfTypeAsList(resourceList1, PsiResourceListElement.class);
      final List<PsiResourceListElement> resources2 = PsiTreeUtil.getChildrenOfTypeAsList(resourceList2, PsiResourceListElement.class);
      for (int i = 0, size = resources1.size(); i < size; i++) {
        final PsiResourceListElement resource1 = resources1.get(i);
        final PsiResourceListElement resource2 = resources2.get(i);
        if (resource1 instanceof PsiResourceVariable && resource2 instanceof PsiResourceVariable) {
          if (!localVariablesAreEquivalent((PsiLocalVariable)resource1, (PsiLocalVariable)resource2)) {
            return false;
          }
        }
        else if (resource1 instanceof PsiResourceExpression && resource2 instanceof PsiResourceExpression) {
          if (!expressionsAreEquivalent(((PsiResourceExpression)resource1).getExpression(), ((PsiResourceExpression)resource2).getExpression())) {
            return false;
          }
        }
        else {
          return false;
        }
      }
    }
    else if (resourceList2 != null) {
      return false;
    }
    final PsiParameter[] catchParameters1 = statement1.getCatchBlockParameters();
    final PsiParameter[] catchParameters2 = statement2.getCatchBlockParameters();
    if (catchParameters1.length != catchParameters2.length) {
      return false;
    }
    for (int i = 0; i < catchParameters2.length; i++) {
      if (!parametersAreEquivalent(catchParameters2[i], catchParameters1[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean parametersAreEquivalent(
    @NotNull PsiParameter parameter1,
    @NotNull PsiParameter parameter2) {
    final PsiType type1 = parameter1.getType();
    final PsiType type2 = parameter2.getType();
    if (!typesAreEquivalent(type1, type2)) {
      return false;
    }
    final String name1 = parameter1.getName();
    final String name2 = parameter2.getName();
    if (name1 == null) {
      return name2 == null;
    }
    return name1.equals(name2);
  }

  public static boolean typesAreEquivalent(
    @Nullable PsiType type1, @Nullable PsiType type2) {
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

  private static boolean whileStatementsAreEquivalent(
    @NotNull PsiWhileStatement statement1,
    @NotNull PsiWhileStatement statement2) {
    final PsiExpression condition1 = statement1.getCondition();
    final PsiExpression condition2 = statement2.getCondition();
    final PsiStatement body1 = statement1.getBody();
    final PsiStatement body2 = statement2.getBody();
    return expressionsAreEquivalent(condition1, condition2) &&
           statementsAreEquivalent(body1, body2);
  }

  private static boolean forStatementsAreEquivalent(
    @NotNull PsiForStatement statement1,
    @NotNull PsiForStatement statement2) {
    final PsiExpression condition1 = statement1.getCondition();
    final PsiExpression condition2 = statement2.getCondition();
    if (!expressionsAreEquivalent(condition1, condition2)) {
      return false;
    }
    final PsiStatement initialization1 = statement1.getInitialization();
    final PsiStatement initialization2 = statement2.getInitialization();
    if (!statementsAreEquivalent(initialization1, initialization2)) {
      return false;
    }
    final PsiStatement update1 = statement1.getUpdate();
    final PsiStatement update2 = statement2.getUpdate();
    if (!statementsAreEquivalent(update1, update2)) {
      return false;
    }
    final PsiStatement body1 = statement1.getBody();
    final PsiStatement body2 = statement2.getBody();
    return statementsAreEquivalent(body1, body2);
  }

  private static boolean forEachStatementsAreEquivalent(
    @NotNull PsiForeachStatement statement1,
    @NotNull PsiForeachStatement statement2) {
    final PsiExpression value1 = statement1.getIteratedValue();
    final PsiExpression value2 = statement2.getIteratedValue();
    if (!expressionsAreEquivalent(value1, value2)) {
      return false;
    }
    final PsiParameter parameter1 = statement1.getIterationParameter();
    final PsiParameter parameter2 = statement1.getIterationParameter();
    final String name1 = parameter1.getName();
    if (name1 == null) {
      return parameter2.getName() == null;
    }
    if (!name1.equals(parameter2.getName())) {
      return false;
    }
    final PsiType type1 = parameter1.getType();
    if (!type1.equals(parameter2.getType())) {
      return false;
    }
    final PsiStatement body1 = statement1.getBody();
    final PsiStatement body2 = statement2.getBody();
    return statementsAreEquivalent(body1, body2);
  }

  private static boolean switchStatementsAreEquivalent(
    @NotNull PsiSwitchStatement statement1,
    @NotNull PsiSwitchStatement statement2) {
    final PsiExpression switchExpression1 = statement1.getExpression();
    final PsiExpression swithcExpression2 = statement2.getExpression();
    final PsiCodeBlock body1 = statement1.getBody();
    final PsiCodeBlock body2 = statement2.getBody();
    return expressionsAreEquivalent(switchExpression1, swithcExpression2) &&
           codeBlocksAreEquivalent(body1, body2);
  }

  private static boolean doWhileStatementsAreEquivalent(
    @NotNull PsiDoWhileStatement statement1,
    @NotNull PsiDoWhileStatement statement2) {
    final PsiExpression condition1 = statement1.getCondition();
    final PsiExpression condition2 = statement2.getCondition();
    final PsiStatement body1 = statement1.getBody();
    final PsiStatement body2 = statement2.getBody();
    return expressionsAreEquivalent(condition1, condition2) &&
           statementsAreEquivalent(body1, body2);
  }

  private static boolean assertStatementsAreEquivalent(
    @NotNull PsiAssertStatement statement1,
    @NotNull PsiAssertStatement statement2) {
    final PsiExpression condition1 = statement1.getAssertCondition();
    final PsiExpression condition2 = statement2.getAssertCondition();
    final PsiExpression description1 = statement1.getAssertDescription();
    final PsiExpression description2 = statement2.getAssertDescription();
    return expressionsAreEquivalent(condition1, condition2) &&
           expressionsAreEquivalent(description1, description2);
  }

  private static boolean synchronizedStatementsAreEquivalent(
    @NotNull PsiSynchronizedStatement statement1,
    @NotNull PsiSynchronizedStatement statement2) {
    final PsiExpression lock1 = statement1.getLockExpression();
    final PsiExpression lock2 = statement2.getLockExpression();
    final PsiCodeBlock body1 = statement1.getBody();
    final PsiCodeBlock body2 = statement2.getBody();
    return expressionsAreEquivalent(lock1, lock2) &&
           codeBlocksAreEquivalent(body1, body2);
  }

  private static boolean blockStatementsAreEquivalent(
    @NotNull PsiBlockStatement statement1,
    @NotNull PsiBlockStatement statement2) {
    final PsiCodeBlock block1 = statement1.getCodeBlock();
    final PsiCodeBlock block2 = statement2.getCodeBlock();
    return codeBlocksAreEquivalent(block1, block2);
  }

  private static boolean breakStatementsAreEquivalent(
    @NotNull PsiBreakStatement statement1,
    @NotNull PsiBreakStatement statement2) {
    final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
    final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
    if (identifier1 == null) {
      return identifier2 == null;
    }
    if (identifier2 == null) {
      return false;
    }
    final String text1 = identifier1.getText();
    final String text2 = identifier2.getText();
    return text1.equals(text2);
  }

  private static boolean continueStatementsAreEquivalent(
    @NotNull PsiContinueStatement statement1,
    @NotNull PsiContinueStatement statement2) {
    final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
    final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
    if (identifier1 == null) {
      return identifier2 == null;
    }
    if (identifier2 == null) {
      return false;
    }
    final String text1 = identifier1.getText();
    final String text2 = identifier2.getText();
    return text1.equals(text2);
  }

  private static boolean switchLabelStatementsAreEquivalent(
    @NotNull PsiSwitchLabelStatement statement1,
    @NotNull PsiSwitchLabelStatement statement2) {
    if (statement1.isDefaultCase()) {
      return statement2.isDefaultCase();
    }
    if (statement2.isDefaultCase()) {
      return false;
    }
    final PsiExpression caseExpression1 = statement1.getCaseValue();
    final PsiExpression caseExpression2 = statement2.getCaseValue();
    return expressionsAreEquivalent(caseExpression1, caseExpression2);
  }

  private static boolean labeledStatementsAreEquivalent(
    @NotNull PsiLabeledStatement statement1,
    @NotNull PsiLabeledStatement statement2) {
    final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
    final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
    final String text1 = identifier1.getText();
    final String text2 = identifier2.getText();
    return text1.equals(text2);
  }

  public static boolean codeBlocksAreEquivalent(
    @Nullable PsiCodeBlock block1, @Nullable PsiCodeBlock block2) {
    if (block1 == null && block2 == null) {
      return true;
    }
    if (block1 == null || block2 == null) {
      return false;
    }
    final PsiStatement[] statements1 = block1.getStatements();
    final PsiStatement[] statements2 = block2.getStatements();
    if (statements2.length != statements1.length) {
      return false;
    }
    for (int i = 0; i < statements2.length; i++) {
      if (!statementsAreEquivalent(statements2[i], statements1[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean ifStatementsAreEquivalent(
    @NotNull PsiIfStatement statement1,
    @NotNull PsiIfStatement statement2) {
    final PsiExpression condition1 = statement1.getCondition();
    final PsiExpression condition2 = statement2.getCondition();
    final PsiStatement thenBranch1 = statement1.getThenBranch();
    final PsiStatement thenBranch2 = statement2.getThenBranch();
    final PsiStatement elseBranch1 = statement1.getElseBranch();
    final PsiStatement elseBranch2 = statement2.getElseBranch();
    return expressionsAreEquivalent(condition1, condition2) &&
           statementsAreEquivalent(thenBranch1, thenBranch2) &&
           statementsAreEquivalent(elseBranch1, elseBranch2);
  }

  private static boolean expressionStatementsAreEquivalent(
    @NotNull PsiExpressionStatement statement1,
    @NotNull PsiExpressionStatement statement2) {
    final PsiExpression expression1 = statement1.getExpression();
    final PsiExpression expression2 = statement2.getExpression();
    return expressionsAreEquivalent(expression1, expression2);
  }

  private static boolean returnStatementsAreEquivalent(
    @NotNull PsiReturnStatement statement1,
    @NotNull PsiReturnStatement statement2) {
    final PsiExpression returnValue1 = statement1.getReturnValue();
    final PsiExpression returnValue2 = statement2.getReturnValue();
    return expressionsAreEquivalent(returnValue1, returnValue2);
  }

  private static boolean throwStatementsAreEquivalent(
    @NotNull PsiThrowStatement statement1,
    @NotNull PsiThrowStatement statement2) {
    final PsiExpression exception1 = statement1.getException();
    final PsiExpression exception2 = statement2.getException();
    return expressionsAreEquivalent(exception1, exception2);
  }

  private static boolean expressionListStatementsAreEquivalent(
    @NotNull PsiExpressionListStatement statement1,
    @NotNull PsiExpressionListStatement statement2) {
    final PsiExpressionList expressionList1 =
      statement1.getExpressionList();
    final PsiExpression[] expressions1 = expressionList1.getExpressions();
    final PsiExpressionList expressionList2 =
      statement2.getExpressionList();
    final PsiExpression[] expressions2 = expressionList2.getExpressions();
    return expressionListsAreEquivalent(expressions1, expressions2);
  }

  public static boolean expressionsAreEquivalent(@Nullable PsiExpression expression1, @Nullable PsiExpression expression2) {
    expression1 = ParenthesesUtils.stripParentheses(expression1);
    expression2 = ParenthesesUtils.stripParentheses(expression2);
    if (expression1 == null) {
      return expression2 == null;
    } else if (expression2 == null) {
      return false;
    }
    if (expression1.getClass() != expression2.getClass()) {
      return false;
    }
    if (expression1 instanceof PsiThisExpression) {
      return true;
    }
    else if (expression1 instanceof PsiSuperExpression) {
      return true;
    }
    else if (expression1 instanceof PsiLiteralExpression) {
      return literalExpressionsAreEquivalent((PsiLiteralExpression)expression1, (PsiLiteralExpression)expression2);
    }
    else if (expression1 instanceof PsiClassObjectAccessExpression) {
      return classObjectAccessExpressionsAreEquivalent((PsiClassObjectAccessExpression)expression1,
                                                       (PsiClassObjectAccessExpression)expression2);
    }
    else if (expression1 instanceof PsiReferenceExpression) {
      return referenceExpressionsAreEquivalent((PsiReferenceExpression)expression1, (PsiReferenceExpression)expression2);
    }
    else if (expression1 instanceof PsiMethodCallExpression) {
      return methodCallExpressionsAreEquivalent((PsiMethodCallExpression)expression1, (PsiMethodCallExpression)expression2);
    }
    else if (expression1 instanceof PsiNewExpression) {
      return newExpressionsAreEquivalent((PsiNewExpression)expression1, (PsiNewExpression)expression2);
    }
    else if (expression1 instanceof PsiArrayInitializerExpression) {
      return arrayInitializerExpressionsAreEquivalent((PsiArrayInitializerExpression)expression1,
                                                      (PsiArrayInitializerExpression)expression2);
    }
    else if (expression1 instanceof PsiTypeCastExpression) {
      return typeCastExpressionsAreEquivalent((PsiTypeCastExpression)expression1, (PsiTypeCastExpression)expression2);
    }
    else if (expression1 instanceof PsiArrayAccessExpression) {
      return arrayAccessExpressionsAreEquivalent((PsiArrayAccessExpression)expression2, (PsiArrayAccessExpression)expression1);
    }
    else if (expression1 instanceof PsiPrefixExpression) {
      return prefixExpressionsAreEquivalent((PsiPrefixExpression)expression1, (PsiPrefixExpression)expression2);
    }
    else if (expression1 instanceof PsiPostfixExpression) {
      return postfixExpressionsAreEquivalent((PsiPostfixExpression)expression1, (PsiPostfixExpression)expression2);
    }
    else if (expression1 instanceof PsiPolyadicExpression) {
      return polyadicExpressionsAreEquivalent((PsiPolyadicExpression)expression1, (PsiPolyadicExpression)expression2);
    }
    else if (expression1 instanceof PsiAssignmentExpression) {
      return assignmentExpressionsAreEquivalent((PsiAssignmentExpression)expression1, (PsiAssignmentExpression)expression2);
    }
    else if (expression1 instanceof PsiConditionalExpression) {
      return conditionalExpressionsAreEquivalent((PsiConditionalExpression)expression1, (PsiConditionalExpression)expression2);
    }
    else if (expression1 instanceof PsiInstanceOfExpression) {
      return instanceofExpressionsAreEquivalent((PsiInstanceOfExpression)expression1, (PsiInstanceOfExpression)expression2);
    }
    return false;
  }

  private static boolean literalExpressionsAreEquivalent(PsiLiteralExpression expression1, PsiLiteralExpression expression2) {
    final Object value1 = expression1.getValue();
    final Object value2 = expression2.getValue();
    if (value1 == null) {
      return value2 == null;
    } else if (value2 == null) {
      return false;
    }
    return value1.equals(value2);
  }

  private static boolean classObjectAccessExpressionsAreEquivalent(PsiClassObjectAccessExpression expression1,
                                                                   PsiClassObjectAccessExpression expression2) {
    final PsiTypeElement operand1 = expression1.getOperand();
    final PsiTypeElement operand2 = expression2.getOperand();
    return typeElementsAreEquivalent(operand1, operand2);
  }

  private static boolean referenceExpressionsAreEquivalent(
    PsiReferenceExpression referenceExpression1,
    PsiReferenceExpression referenceExpression2) {
    final PsiElement element1 = referenceExpression1.resolve();
    final PsiElement element2 = referenceExpression2.resolve();
    if (element1 != null) {
      if (!element1.equals(element2)) {
        return false;
      }
    }
    else {
      return false; // incomplete code
    }
    if (element1 instanceof PsiMember) {
      final PsiMember member1 = (PsiMember)element1;
      if (member1.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
      else if (member1 instanceof PsiClass) {
        return true;
      }
    }
    else {
      return true;
    }
    final PsiExpression qualifier1 =
      referenceExpression1.getQualifierExpression();
    final PsiExpression qualifier2 =
      referenceExpression2.getQualifierExpression();
    if (qualifier1 != null &&
        !(qualifier1 instanceof PsiThisExpression ||
          qualifier1 instanceof PsiSuperExpression)) {
      if (qualifier2 == null) {
        return false;
      }
      else if (!expressionsAreEquivalent(qualifier1, qualifier2)) {
        return false;
      }
    }
    else {
      if (qualifier2 != null &&
          !(qualifier2 instanceof PsiThisExpression ||
            qualifier2 instanceof PsiSuperExpression)) {
        return false;
      }
    }
    final String text1 = referenceExpression1.getText();
    final String text2 = referenceExpression2.getText();
    return text1.equals(text2);
  }

  private static boolean instanceofExpressionsAreEquivalent(
    PsiInstanceOfExpression instanceOfExpression1,
    PsiInstanceOfExpression instanceOfExpression2) {
    final PsiExpression operand1 = instanceOfExpression1.getOperand();
    final PsiExpression operand2 = instanceOfExpression2.getOperand();
    if (!expressionsAreEquivalent(operand1, operand2)) {
      return false;
    }
    final PsiTypeElement typeElement1 = instanceOfExpression1.getCheckType();
    final PsiTypeElement typeElement2 = instanceOfExpression2.getCheckType();
    return typeElementsAreEquivalent(typeElement1, typeElement2);
  }

  private static boolean typeElementsAreEquivalent(PsiTypeElement typeElement1, PsiTypeElement typeElement2) {
    if (typeElement1 == null) {
      return typeElement2 == null;
    }
    else if (typeElement2 == null) {
      return false;
    }
    final PsiType type1 = typeElement1.getType();
    final PsiType type2 = typeElement2.getType();
    return typesAreEquivalent(type1, type2);
  }

  private static boolean methodCallExpressionsAreEquivalent(
    @NotNull PsiMethodCallExpression methodCallExpression1,
    @NotNull PsiMethodCallExpression methodCallExpression2) {
    final PsiReferenceExpression methodExpression1 =
      methodCallExpression1.getMethodExpression();
    final PsiReferenceExpression methodExpression2 =
      methodCallExpression2.getMethodExpression();
    if (!expressionsAreEquivalent(methodExpression1, methodExpression2)) {
      return false;
    }
    final PsiExpressionList argumentList1 =
      methodCallExpression1.getArgumentList();
    final PsiExpression[] args1 = argumentList1.getExpressions();
    final PsiExpressionList argumentList2 =
      methodCallExpression2.getArgumentList();
    final PsiExpression[] args2 = argumentList2.getExpressions();
    return expressionListsAreEquivalent(args1, args2);
  }

  private static boolean newExpressionsAreEquivalent(
    @NotNull PsiNewExpression newExpression1,
    @NotNull PsiNewExpression newExpression2) {
    final PsiJavaCodeReferenceElement classReference1 =
      newExpression1.getClassReference();
    final PsiJavaCodeReferenceElement classReference2 =
      newExpression2.getClassReference();
    if (classReference1 == null || classReference2 == null) {
      return false;
    }
    final PsiElement target1 = classReference1.resolve();
    if (target1 == null) {
      return false;
    }
    final PsiElement target2 = classReference2.resolve();
    if (!target1.equals(target2)) {
      return false;
    }
    final PsiExpression[] arrayDimensions1 =
      newExpression1.getArrayDimensions();
    final PsiExpression[] arrayDimensions2 =
      newExpression2.getArrayDimensions();
    if (!expressionListsAreEquivalent(arrayDimensions1, arrayDimensions2)) {
      return false;
    }
    final PsiArrayInitializerExpression arrayInitializer1 =
      newExpression1.getArrayInitializer();
    final PsiArrayInitializerExpression arrayInitializer2 =
      newExpression2.getArrayInitializer();
    if (!expressionsAreEquivalent(arrayInitializer1, arrayInitializer2)) {
      return false;
    }
    final PsiExpression qualifier1 = newExpression1.getQualifier();
    final PsiExpression qualifier2 = newExpression2.getQualifier();
    if (!expressionsAreEquivalent(qualifier1, qualifier2)) {
      return false;
    }
    final PsiExpressionList argumentList1 = newExpression1.getArgumentList();
    final PsiExpression[] args1;
    if (argumentList1 == null) {
      args1 = null;
    }
    else {
      args1 = argumentList1.getExpressions();
    }
    final PsiExpressionList argumentList2 = newExpression2.getArgumentList();
    final PsiExpression[] args2;
    if (argumentList2 == null) {
      args2 = null;
    }
    else {
      args2 = argumentList2.getExpressions();
    }
    return expressionListsAreEquivalent(args1, args2);
  }

  private static boolean arrayInitializerExpressionsAreEquivalent(
    @NotNull PsiArrayInitializerExpression arrayInitializerExpression1,
    @NotNull PsiArrayInitializerExpression arrayInitializerExpression2) {
    final PsiExpression[] initializers1 =
      arrayInitializerExpression1.getInitializers();
    final PsiExpression[] initializers2 =
      arrayInitializerExpression2.getInitializers();
    return expressionListsAreEquivalent(initializers1, initializers2);
  }

  private static boolean typeCastExpressionsAreEquivalent(
    @NotNull PsiTypeCastExpression typeCastExpression1,
    @NotNull PsiTypeCastExpression typeCastExpression2) {
    final PsiTypeElement typeElement1 = typeCastExpression1.getCastType();
    final PsiTypeElement typeElement2 = typeCastExpression2.getCastType();
    if (!typeElementsAreEquivalent(typeElement1, typeElement2)) {
      return false;
    }
    final PsiExpression operand1 = typeCastExpression1.getOperand();
    final PsiExpression operand2 = typeCastExpression2.getOperand();
    return expressionsAreEquivalent(operand1, operand2);
  }

  private static boolean arrayAccessExpressionsAreEquivalent(
    @NotNull PsiArrayAccessExpression arrayAccessExpression1,
    @NotNull PsiArrayAccessExpression arrayAccessExpression2) {
    final PsiExpression arrayExpression2 =
      arrayAccessExpression1.getArrayExpression();
    final PsiExpression arrayExpression1 =
      arrayAccessExpression2.getArrayExpression();
    final PsiExpression indexExpression2 =
      arrayAccessExpression1.getIndexExpression();
    final PsiExpression indexExpression1 =
      arrayAccessExpression2.getIndexExpression();
    return expressionsAreEquivalent(arrayExpression2, arrayExpression1)
           && expressionsAreEquivalent(indexExpression2, indexExpression1);
  }

  private static boolean prefixExpressionsAreEquivalent(
    @NotNull PsiPrefixExpression prefixExpression1,
    @NotNull PsiPrefixExpression prefixExpression2) {
    final IElementType tokenType1 = prefixExpression1.getOperationTokenType();
    if (!tokenType1.equals(prefixExpression2.getOperationTokenType())) {
      return false;
    }
    final PsiExpression operand1 = prefixExpression1.getOperand();
    final PsiExpression operand2 = prefixExpression2.getOperand();
    return expressionsAreEquivalent(operand1, operand2);
  }

  private static boolean postfixExpressionsAreEquivalent(
    @NotNull PsiPostfixExpression postfixExpression1,
    @NotNull PsiPostfixExpression postfixExpression2) {
    final IElementType tokenType1 = postfixExpression1.getOperationTokenType();
    if (!tokenType1.equals(postfixExpression2.getOperationTokenType())) {
      return false;
    }
    final PsiExpression operand1 = postfixExpression1.getOperand();
    final PsiExpression operand2 = postfixExpression2.getOperand();
    return expressionsAreEquivalent(operand1, operand2);
  }

  private static boolean polyadicExpressionsAreEquivalent(
    @NotNull PsiPolyadicExpression polyadicExpression1,
    @NotNull PsiPolyadicExpression polyadicExpression2) {
    final IElementType tokenType1 = polyadicExpression1.getOperationTokenType();
    final IElementType tokenType2 = polyadicExpression2.getOperationTokenType();
    if (!tokenType1.equals(tokenType2)) {
      return false;
    }
    final PsiExpression[] operands1 = polyadicExpression1.getOperands();
    final PsiExpression[] operands2 = polyadicExpression2.getOperands();
    if (operands1.length != operands2.length) {
      return false;
    }
    for (int i = 0, length = operands1.length; i < length; i++) {
      if (!expressionsAreEquivalent(operands1[i], operands2[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean assignmentExpressionsAreEquivalent(
    @NotNull PsiAssignmentExpression assignmentExpression1,
    @NotNull PsiAssignmentExpression assignmentExpression2) {
    final IElementType tokenType1 = assignmentExpression1.getOperationTokenType();
    if (!tokenType1.equals(assignmentExpression2.getOperationTokenType())) {
      return false;
    }
    final PsiExpression lhs1 = assignmentExpression1.getLExpression();
    final PsiExpression lhs2 = assignmentExpression2.getLExpression();
    final PsiExpression rhs1 = assignmentExpression1.getRExpression();
    final PsiExpression rhs2 = assignmentExpression2.getRExpression();
    return expressionsAreEquivalent(lhs1, lhs2)
           && expressionsAreEquivalent(rhs1, rhs2);
  }

  private static boolean conditionalExpressionsAreEquivalent(
    @NotNull PsiConditionalExpression conditionalExpression1,
    @NotNull PsiConditionalExpression conditionalExpression2) {
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
    return expressionsAreEquivalent(condition1, condition2)
           && expressionsAreEquivalent(thenExpression1, thenExpression2)
           && expressionsAreEquivalent(elseExpression1, elseExpression2);
  }

  private static boolean expressionListsAreEquivalent(
    @Nullable PsiExpression[] expressions1,
    @Nullable PsiExpression[] expressions2) {
    if (expressions1 == null && expressions2 == null) {
      return true;
    }
    if (expressions1 == null || expressions2 == null) {
      return false;
    }
    if (expressions1.length != expressions2.length) {
      return false;
    }
    for (int i = 0; i < expressions1.length; i++) {
      if (!expressionsAreEquivalent(expressions1[i], expressions2[i])) {
        return false;
      }
    }
    return true;
  }
}