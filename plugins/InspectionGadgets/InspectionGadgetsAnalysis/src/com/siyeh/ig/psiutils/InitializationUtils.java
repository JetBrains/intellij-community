/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class InitializationUtils {

  private InitializationUtils() {}

  public static boolean methodAssignsVariableOrFails(@Nullable PsiMethod method, @NotNull PsiVariable variable) {
    return methodAssignsVariableOrFails(method, variable, false);
  }

  public static boolean expressionAssignsVariableOrFails(@Nullable PsiExpression expression, @NotNull PsiVariable variable) {
    return expressionAssignsVariableOrFails(expression, variable, new HashSet(), true);
  }

  public static boolean methodAssignsVariableOrFails(@Nullable PsiMethod method, @NotNull PsiVariable variable, boolean strict) {
    if (method == null) {
      return false;
    }
    return blockAssignsVariableOrFails(method.getBody(), variable, strict);
  }

  public static boolean blockAssignsVariableOrFails(@Nullable PsiCodeBlock block, @NotNull PsiVariable variable) {
    return blockAssignsVariableOrFails(block, variable, false);
  }

  public static boolean blockAssignsVariableOrFails(@Nullable PsiCodeBlock block, @NotNull PsiVariable variable, boolean strict) {
    return blockAssignsVariableOrFails(block, variable, new HashSet<>(), strict);
  }

  private static boolean blockAssignsVariableOrFails(@Nullable PsiCodeBlock block, @NotNull PsiVariable variable,
                                                     @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    if (block == null) {
      return false;
    }
    int assignmentCount = 0;
    for (final PsiStatement statement : block.getStatements()) {
      if (statementAssignsVariableOrFails(statement, variable, checkedMethods, strict)) {
        if (strict) {
          assignmentCount++;
        }
        else {
          return true;
        }
      }
    }
    return assignmentCount == 1;
  }

  private static boolean statementAssignsVariableOrFails(@Nullable PsiStatement statement, PsiVariable variable,
                                                         @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    if (statement == null) {
      return false;
    }
    if (ExceptionUtils.statementThrowsException(statement)) {
      return true;
    }
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiAssertStatement ||
        statement instanceof PsiEmptyStatement ||
        statement instanceof PsiSwitchLabelStatement) {
      return false;
    }
    else if (statement instanceof PsiReturnStatement) {
      final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      return expressionAssignsVariableOrFails(returnStatement.getReturnValue(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiThrowStatement) {
      final PsiThrowStatement throwStatement = (PsiThrowStatement)statement;
      return expressionAssignsVariableOrFails(throwStatement.getException(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiExpressionListStatement) {
      final PsiExpressionListStatement list = (PsiExpressionListStatement)statement;
      final PsiExpressionList expressionList = list.getExpressionList();
      for (final PsiExpression expression : expressionList.getExpressions()) {
        if (expressionAssignsVariableOrFails(expression, variable, checkedMethods, strict)) {
          return true;
        }
      }
      return false;
    }
    else if (statement instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      return expressionAssignsVariableOrFails(expressionStatement.getExpression(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiDeclarationStatement) {
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      return declarationStatementAssignsVariableOrFails(declarationStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)statement;
      return forStatementAssignsVariableOrFails(forStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiForeachStatement) {
      final PsiForeachStatement foreachStatement = (PsiForeachStatement)statement;
      return foreachStatementAssignsVariableOrFails(foreachStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiWhileStatement) {
      final PsiWhileStatement whileStatement = (PsiWhileStatement)statement;
      return whileStatementAssignsVariableOrFails(whileStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      final PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)statement;
      return doWhileAssignsVariableOrFails(doWhileStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      final PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)statement;
      return blockAssignsVariableOrFails(synchronizedStatement.getBody(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
      return blockAssignsVariableOrFails(blockStatement.getCodeBlock(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiLabeledStatement) {
      final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)statement;
      return statementAssignsVariableOrFails(labeledStatement.getStatement(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiIfStatement) {
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      return ifStatementAssignsVariableOrFails(ifStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiTryStatement) {
      final PsiTryStatement tryStatement = (PsiTryStatement)statement;
      return tryStatementAssignsVariableOrFails(tryStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiSwitchStatement) {
      final PsiSwitchStatement switchStatement = (PsiSwitchStatement)statement;
      return switchStatementAssignsVariableOrFails(switchStatement, variable, checkedMethods, strict);
    }
    else {
      // unknown statement type
      return false;
    }
  }

  public static boolean switchStatementAssignsVariableOrFails(@NotNull PsiSwitchStatement switchStatement, @NotNull PsiVariable variable,
                                                              boolean strict) {
    return switchStatementAssignsVariableOrFails(switchStatement, variable, new HashSet(), strict);
  }

  private static boolean switchStatementAssignsVariableOrFails(@NotNull PsiSwitchStatement switchStatement, @NotNull PsiVariable variable,
                                                               @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpression expression = switchStatement.getExpression();
    if (expressionAssignsVariableOrFails(expression, variable, checkedMethods, strict)) {
      return true;
    }
    final PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return false;
    }
    final PsiStatement[] statements = body.getStatements();
    boolean containsDefault = false;
    boolean assigns = false;
    for (int i = 0; i < statements.length; i++) {
      final PsiStatement statement = statements[i];
      if (statement instanceof PsiSwitchLabelStatement) {
        final PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)statement;
        if (i == statements.length - 1) {
          return false;
        }
        if (labelStatement.isDefaultCase()) {
          containsDefault = true;
        }
        assigns = false;
      }
      else if (statement instanceof PsiBreakStatement) {
        final PsiBreakStatement breakStatement = (PsiBreakStatement)statement;
        if (breakStatement.getLabelIdentifier() != null) {
          return false;
        }
        if (!assigns) {
          return false;
        }
        assigns = false;
      }
      else {
        assigns |= statementAssignsVariableOrFails(statement, variable, checkedMethods, strict);
        if (i == statements.length - 1 && !assigns) {
          return false;
        }
      }
    }
    return containsDefault;
  }

  private static boolean declarationStatementAssignsVariableOrFails(PsiDeclarationStatement declarationStatement, PsiVariable variable,
                                                                    Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiElement[] elements = declarationStatement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiVariable) {
        final PsiVariable declaredVariable = (PsiVariable)element;
        if (expressionAssignsVariableOrFails(declaredVariable.getInitializer(), variable, checkedMethods, strict)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean tryStatementAssignsVariableOrFails(@NotNull PsiTryStatement tryStatement, PsiVariable variable,
                                                            @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      for (PsiResourceListElement resource : resourceList) {
        if (resource instanceof PsiResourceVariable) {
          final PsiExpression initializer = ((PsiResourceVariable)resource).getInitializer();
          if (expressionAssignsVariableOrFails(initializer, variable, checkedMethods, strict)) {
            return true;
          }
        }
      }
    }
    boolean initializedInTryAndCatch = blockAssignsVariableOrFails(tryStatement.getTryBlock(), variable, checkedMethods, strict);
    final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (final PsiCodeBlock catchBlock : catchBlocks) {
      if (strict) {
        initializedInTryAndCatch &= ExceptionUtils.blockThrowsException(catchBlock);
      }
      else {
        initializedInTryAndCatch &= blockAssignsVariableOrFails(catchBlock, variable, checkedMethods, strict);
      }
    }
    return initializedInTryAndCatch || blockAssignsVariableOrFails(tryStatement.getFinallyBlock(), variable, checkedMethods, strict);
  }

  private static boolean ifStatementAssignsVariableOrFails(@NotNull PsiIfStatement ifStatement, PsiVariable variable,
                                                           @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpression condition = ifStatement.getCondition();
    if (expressionAssignsVariableOrFails(condition, variable, checkedMethods, strict)) {
      return true;
    }
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (BoolUtils.isTrue(condition)) {
      return statementAssignsVariableOrFails(thenBranch, variable, checkedMethods, strict);
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (BoolUtils.isFalse(condition)) {
      return statementAssignsVariableOrFails(elseBranch, variable, checkedMethods, strict);
    }
    return statementAssignsVariableOrFails(thenBranch, variable, checkedMethods, strict) &&
           statementAssignsVariableOrFails(elseBranch, variable, checkedMethods, strict);
  }

  private static boolean doWhileAssignsVariableOrFails(@NotNull PsiDoWhileStatement doWhileStatement, PsiVariable variable,
                                                       @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    return statementAssignsVariableOrFails(doWhileStatement.getBody(), variable, checkedMethods, strict) ||
           expressionAssignsVariableOrFails(doWhileStatement.getCondition(), variable, checkedMethods, strict);
  }

  private static boolean whileStatementAssignsVariableOrFails(@NotNull PsiWhileStatement whileStatement, PsiVariable variable,
                                                              @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpression condition = whileStatement.getCondition();
    if (expressionAssignsVariableOrFails(condition, variable, checkedMethods, strict)) {
      return true;
    }
    if (BoolUtils.isTrue(condition)) {
      final PsiStatement body = whileStatement.getBody();
      if (statementAssignsVariableOrFails(body, variable, checkedMethods, strict)) {
        return true;
      }
    }
    return false;
  }

  private static boolean forStatementAssignsVariableOrFails(@NotNull PsiForStatement forStatement, PsiVariable variable,
                                                            @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    if (statementAssignsVariableOrFails(forStatement.getInitialization(), variable, checkedMethods, strict)) {
      return true;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (expressionAssignsVariableOrFails(condition, variable, checkedMethods, strict)) {
      return true;
    }
    if (BoolUtils.isTrue(condition)) {
      if (statementAssignsVariableOrFails(forStatement.getBody(), variable, checkedMethods, strict)) {
        return true;
      }
      if (statementAssignsVariableOrFails(forStatement.getUpdate(), variable, checkedMethods, strict)) {
        return true;
      }
    }
    return false;
  }

  private static boolean foreachStatementAssignsVariableOrFails(@NotNull PsiForeachStatement foreachStatement, PsiVariable field,
                                                                @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    return expressionAssignsVariableOrFails(foreachStatement.getIteratedValue(), field, checkedMethods, strict);
  }

  private static boolean expressionAssignsVariableOrFails(@Nullable PsiExpression expression, PsiVariable variable,
                                                          @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    if (expression == null) {
      return false;
    }
    if (expression instanceof PsiThisExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiSuperExpression ||
        expression instanceof PsiClassObjectAccessExpression ||
        expression instanceof PsiReferenceExpression) {
      return false;
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      return expressionAssignsVariableOrFails(parenthesizedExpression.getExpression(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      return methodCallAssignsVariableOrFails(methodCallExpression, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      return newExpressionAssignsVariableOrFails(newExpression, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiArrayInitializerExpression) {
      final PsiArrayInitializerExpression array = (PsiArrayInitializerExpression)expression;
      for (final PsiExpression initializer : array.getInitializers()) {
        if (expressionAssignsVariableOrFails(initializer, variable, checkedMethods, strict)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCast = (PsiTypeCastExpression)expression;
      return expressionAssignsVariableOrFails(typeCast.getOperand(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression accessExpression = (PsiArrayAccessExpression)expression;
      return expressionAssignsVariableOrFails(accessExpression.getArrayExpression(), variable, checkedMethods, strict) ||
             expressionAssignsVariableOrFails(accessExpression.getIndexExpression(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      return expressionAssignsVariableOrFails(prefixExpression.getOperand(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)expression;
      return expressionAssignsVariableOrFails(postfixExpression.getOperand(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (expressionAssignsVariableOrFails(operand, variable, checkedMethods, strict)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditional = (PsiConditionalExpression)expression;
      if (expressionAssignsVariableOrFails(conditional.getCondition(), variable, checkedMethods, strict)) {
        return true;
      }
      return expressionAssignsVariableOrFails(conditional.getThenExpression(), variable, checkedMethods, strict) &&
             expressionAssignsVariableOrFails(conditional.getElseExpression(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      final PsiExpression lhs = assignment.getLExpression();
      if (expressionAssignsVariableOrFails(lhs, variable, checkedMethods, strict)) {
        return true;
      }
      if (expressionAssignsVariableOrFails(assignment.getRExpression(), variable, checkedMethods, strict)) {
        return true;
      }
      if (lhs instanceof PsiReferenceExpression) {
        final PsiElement element = ((PsiReference)lhs).resolve();
        if (variable.equals(element)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
      return expressionAssignsVariableOrFails(instanceOfExpression.getOperand(), variable, checkedMethods, strict);
    }
    else {
      return false;
    }
  }

  private static boolean newExpressionAssignsVariableOrFails(@NotNull PsiNewExpression newExpression, PsiVariable variable,
                                                             @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList != null) {
      for (final PsiExpression argument : argumentList.getExpressions()) {
        if (expressionAssignsVariableOrFails(argument, variable, checkedMethods, strict)) {
          return true;
        }
      }
    }
    if (expressionAssignsVariableOrFails(newExpression.getArrayInitializer(), variable, checkedMethods, strict)) {
      return true;
    }
    for (final PsiExpression dimension : newExpression.getArrayDimensions()) {
      if (expressionAssignsVariableOrFails(dimension, variable, checkedMethods, strict)) {
        return true;
      }
    }
    return false;
  }

  private static boolean methodCallAssignsVariableOrFails(@NotNull PsiMethodCallExpression callExpression, PsiVariable variable,
                                                          @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpressionList argumentList = callExpression.getArgumentList();
    for (final PsiExpression argument : argumentList.getExpressions()) {
      if (expressionAssignsVariableOrFails(argument, variable, checkedMethods, strict)) {
        return true;
      }
    }
    if (expressionAssignsVariableOrFails(callExpression.getMethodExpression(), variable, checkedMethods, strict)) {
      return true;
    }
    final PsiMethod method = callExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    if (!checkedMethods.add(methodSignature)) {
      return false;
    }
    final PsiClass containingClass = ClassUtils.getContainingClass(callExpression);
    final PsiClass calledClass = method.getContainingClass();
    if (calledClass == null || !calledClass.equals(containingClass)) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)
        || method.hasModifierProperty(PsiModifier.PRIVATE)
        || method.hasModifierProperty(PsiModifier.FINAL)
        || method.isConstructor()
        || calledClass.hasModifierProperty(PsiModifier.FINAL)) {
      return blockAssignsVariableOrFails(method.getBody(), variable, checkedMethods, strict);
    }
    return false;
  }
}