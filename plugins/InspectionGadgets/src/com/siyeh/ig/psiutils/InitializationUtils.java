/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

public class InitializationUtils{

    private InitializationUtils(){
    }

    public static boolean methodAssignsVariableOrFails(
            @Nullable PsiMethod method, @NotNull PsiVariable variable) {
        return methodAssignsVariableOrFails(method, variable, false);
    }

    public static boolean expressionAssignsVariableOrFails(
            @Nullable PsiExpression expression, @NotNull PsiVariable variable) {
        return expressionAssignsVariableOrFails(expression, variable,
                new HashSet(), true);
    }

    public static boolean methodAssignsVariableOrFails(
            @Nullable PsiMethod method, @NotNull PsiVariable variable,
            boolean strict) {
        if (method == null) {
            return false;
        }
        final PsiCodeBlock body = method.getBody();
        return body != null && blockAssignsVariableOrFails(body, variable,
                strict);
    }

    public static boolean classInitializerAssignsVariableOrFails(
            @Nullable PsiClassInitializer initializer,
            @NotNull PsiVariable variable, boolean strict) {
        if (initializer == null) {
            return false;
        }
        final PsiCodeBlock body = initializer.getBody();
        return blockAssignsVariableOrFails(body, variable, strict);
    }

    public static boolean blockAssignsVariableOrFails(
            @Nullable PsiCodeBlock block, @NotNull PsiVariable variable) {
        return blockAssignsVariableOrFails(block, variable, false);
    }

    public static boolean blockAssignsVariableOrFails(
            @Nullable PsiCodeBlock block, @NotNull PsiVariable variable,
            boolean strict) {
        return blockAssignsVariableOrFails(block, variable,
                new HashSet<MethodSignature>(), strict);
    }

    private static boolean blockAssignsVariableOrFails(
            @Nullable PsiCodeBlock block, @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods, boolean strict){
        if(block == null){
            return false;
        }
        final PsiStatement[] statements = block.getStatements();
        int assignmentCount = 0;
        for(final PsiStatement statement : statements){
            if(statementAssignsVariableOrFails(statement, variable,
                    checkedMethods, strict)){
                if (strict) {
                    assignmentCount++;
                } else {
                    return true;
                }
            }
        }
        return assignmentCount == 1;
    }

    private static boolean statementAssignsVariableOrFails(
            @Nullable PsiStatement statement, PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods, boolean strict){
        if(statement == null){
            return false;
        }
        if(ExceptionUtils.statementThrowsException(statement)){
            return true;
        }
        if(statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiAssertStatement ||
                statement instanceof PsiEmptyStatement){
            return false;
        } else if(statement instanceof PsiReturnStatement){
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement) statement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            return expressionAssignsVariableOrFails(returnValue, variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiThrowStatement){
            final PsiThrowStatement throwStatement =
                    (PsiThrowStatement) statement;
            final PsiExpression exception = throwStatement.getException();
            return expressionAssignsVariableOrFails(exception, variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiExpressionListStatement){
            final PsiExpressionListStatement list =
                    (PsiExpressionListStatement) statement;
            final PsiExpressionList expressionList = list.getExpressionList();
            final PsiExpression[] expressions = expressionList.getExpressions();
            for(final PsiExpression expression : expressions){
                if(expressionAssignsVariableOrFails(expression, variable,
                        checkedMethods, strict)){
                    return true;
                }
            }
            return false;
        } else if(statement instanceof PsiExpressionStatement){
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement) statement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            return expressionAssignsVariableOrFails(expression, variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement)statement;
            return declarationStatementAssignsVariableOrFails(
                    declarationStatement, variable, checkedMethods, strict);
        } else if(statement instanceof PsiForStatement){
            final PsiForStatement forStatement = (PsiForStatement)statement;
            return forStatementAssignsVariableOrFails(forStatement,
                    variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiForeachStatement){
            final PsiForeachStatement foreachStatement =
                    (PsiForeachStatement)statement;
            return foreachStatementAssignsVariableOrFails(variable,
                    foreachStatement);
        } else if(statement instanceof PsiWhileStatement){
            final PsiWhileStatement whileStatement =
                    (PsiWhileStatement)statement;
            return whileStatementAssignsVariableOrFails(whileStatement,
                    variable, checkedMethods, strict);
        } else if(statement instanceof PsiDoWhileStatement){
            final PsiDoWhileStatement doWhileStatement =
                    (PsiDoWhileStatement)statement;
            return doWhileAssignsVariableOrFails(doWhileStatement, variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiSynchronizedStatement){
            final PsiSynchronizedStatement synchronizedStatement =
                    (PsiSynchronizedStatement)statement;
            final PsiCodeBlock body = synchronizedStatement.getBody();
            return blockAssignsVariableOrFails(body, variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiBlockStatement){
            final PsiBlockStatement blockStatement =
                    (PsiBlockStatement)statement;
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            return blockAssignsVariableOrFails(codeBlock, variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiLabeledStatement){
            final PsiLabeledStatement labeledStatement =
                    (PsiLabeledStatement) statement;
            final PsiStatement statementLabeled =
                    labeledStatement.getStatement();
            return statementAssignsVariableOrFails(statementLabeled, variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiIfStatement){
            final PsiIfStatement ifStatement = (PsiIfStatement)statement;
            return ifStatementAssignsVariableOrFails(ifStatement, variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiTryStatement){
            final PsiTryStatement tryStatement = (PsiTryStatement)statement;
            return tryStatementAssignsVariableOrFails(tryStatement, variable,
                    checkedMethods, strict);
        } else if(statement instanceof PsiSwitchStatement){
            final PsiSwitchStatement switchStatement =
                    (PsiSwitchStatement)statement;
            return switchStatementAssignsVariableOrFails(switchStatement,
                    variable, checkedMethods, strict);
        } else {
            // unknown statement type
            return false;
        }
    }

    public static boolean switchStatementAssignsVariableOrFails(
            @NotNull PsiSwitchStatement switchStatement,
            @NotNull PsiVariable variable,
            boolean strict) {
        return switchStatementAssignsVariableOrFails(switchStatement, variable,
                new HashSet(), strict);
    }

    private static boolean switchStatementAssignsVariableOrFails(
            @NotNull PsiSwitchStatement switchStatement,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
        final PsiExpression expression = switchStatement.getExpression();
        if (expressionAssignsVariableOrFails(expression, variable,
                checkedMethods, strict)) {
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
                final PsiSwitchLabelStatement labelStatement
                        = (PsiSwitchLabelStatement) statement;
                if (i == statements.length - 1) {
                    return false;
                }
                if (labelStatement.isDefaultCase()) {
                    containsDefault = true;
                }
                assigns = false;
            } else if (statement instanceof PsiBreakStatement) {
                final PsiBreakStatement breakStatement
                        = (PsiBreakStatement) statement;
                if (breakStatement.getLabelIdentifier() != null) {
                    return false;
                }
                if (!assigns) {
                    return false;
                }
                assigns = false;
            } else {
                assigns |= statementAssignsVariableOrFails(statement, variable,
                        checkedMethods, strict);
                if (i == statements.length - 1 && !assigns) {
                    return false;
                }
            }
        }
        return containsDefault;
    }

    private static boolean declarationStatementAssignsVariableOrFails(
            PsiDeclarationStatement declarationStatement, PsiVariable variable,
            Set<MethodSignature> checkedMethods, boolean strict){
        final PsiElement[] elements =
                declarationStatement.getDeclaredElements();
        for(PsiElement element : elements){
            if (element instanceof PsiVariable) {
                final PsiVariable declaredVariable = (PsiVariable) element;
                final PsiExpression initializer =
                        declaredVariable.getInitializer();
                if(expressionAssignsVariableOrFails(initializer, variable,
                        checkedMethods, strict)){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tryStatementAssignsVariableOrFails(
            @NotNull PsiTryStatement tryStatement, PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods, boolean strict){
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        boolean initializedInTryAndCatch =
                blockAssignsVariableOrFails(tryBlock, variable,
                        checkedMethods, strict);
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for(final PsiCodeBlock catchBlock : catchBlocks){
            if (strict) {
                initializedInTryAndCatch &=
                        ExceptionUtils.blockThrowsException(catchBlock);
            } else {
                initializedInTryAndCatch &= blockAssignsVariableOrFails(
                        catchBlock, variable,
                        checkedMethods, strict);
            }
        }
        if(initializedInTryAndCatch){
            return true;
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        return blockAssignsVariableOrFails(finallyBlock, variable,
                checkedMethods, strict);
    }

    private static boolean ifStatementAssignsVariableOrFails(
            @NotNull PsiIfStatement ifStatement,
            PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods,
            boolean strict){
        final PsiExpression condition = ifStatement.getCondition();
        if(expressionAssignsVariableOrFails(condition, variable,
                checkedMethods, strict)){
            return true;
        }
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        if (BoolUtils.isTrue(condition)) {
            return statementAssignsVariableOrFails(thenBranch, variable,
                    checkedMethods, strict);
        } else if (BoolUtils.isFalse(condition)) {
            return statementAssignsVariableOrFails(elseBranch, variable,
                    checkedMethods, strict);
        }
        return statementAssignsVariableOrFails(thenBranch, variable,
                checkedMethods, strict) &&
                statementAssignsVariableOrFails(elseBranch, variable,
                        checkedMethods, strict);
    }

    private static boolean doWhileAssignsVariableOrFails(
            @NotNull PsiDoWhileStatement doWhileStatement,
            PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods,
            boolean strict){
        final PsiExpression condition = doWhileStatement.getCondition();
        final PsiStatement body = doWhileStatement.getBody();
        return expressionAssignsVariableOrFails(condition, variable,
                checkedMethods, strict) ||
                statementAssignsVariableOrFails(body, variable, checkedMethods,
                        strict);
    }

    private static boolean whileStatementAssignsVariableOrFails(
            @NotNull PsiWhileStatement whileStatement, PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods,
            boolean strict){
        final PsiExpression condition = whileStatement.getCondition();
        if(expressionAssignsVariableOrFails(condition, variable,
                checkedMethods, strict)){
            return true;
        }
        if(BoolUtils.isTrue(condition)){
            final PsiStatement body = whileStatement.getBody();
            if(statementAssignsVariableOrFails(body, variable, checkedMethods,
                    strict)){
                return true;
            }
        }
        return false;
    }

    private static boolean forStatementAssignsVariableOrFails(
            @NotNull PsiForStatement forStatement, PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods, boolean strict){
        final PsiStatement initialization = forStatement.getInitialization();
        if(statementAssignsVariableOrFails(initialization, variable,
                checkedMethods, strict)){
            return true;
        }
        final PsiExpression test = forStatement.getCondition();
        if(expressionAssignsVariableOrFails(test, variable, checkedMethods,
                strict)){
            return true;
        }
        if(BoolUtils.isTrue(test)){
            final PsiStatement body = forStatement.getBody();
            if(statementAssignsVariableOrFails(body, variable, checkedMethods,
                    strict)){
                return true;
            }
            final PsiStatement update = forStatement.getUpdate();
            if(statementAssignsVariableOrFails(update, variable,
                    checkedMethods, strict)){
                return true;
            }
        }
        return false;
    }

    private static boolean foreachStatementAssignsVariableOrFails(
            PsiVariable field, PsiForeachStatement forStatement){
        return false;
    }

    private static boolean expressionAssignsVariableOrFails(
            @Nullable PsiExpression expression,
            PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods, boolean strict){
        if(expression == null){
            return false;
        }
        if(expression instanceof PsiThisExpression ||
                expression instanceof PsiLiteralExpression ||
                expression instanceof PsiSuperExpression ||
                expression instanceof PsiClassObjectAccessExpression){
            return false;
        } else if(expression instanceof PsiReferenceExpression){
            return false;
        } else if(expression instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)expression;
            return methodCallAssignsVariableOrFails(methodCallExpression,
                    variable, checkedMethods, strict);
        } else if (expression instanceof PsiNewExpression){
            final PsiNewExpression newExpression = (PsiNewExpression)expression;
            return newExpressionAssignsVariableOrFails(newExpression, variable,
                    checkedMethods, strict);
        } else if(expression instanceof PsiArrayInitializerExpression){
            final PsiArrayInitializerExpression array =
                    (PsiArrayInitializerExpression) expression;
            final PsiExpression[] initializers = array.getInitializers();
            for(final PsiExpression initializer : initializers){
                if(expressionAssignsVariableOrFails(initializer, variable,
                        checkedMethods, strict)){
                    return true;
                }
            }
            return false;
        } else if(expression instanceof PsiTypeCastExpression){
            final PsiTypeCastExpression typeCast =
                    (PsiTypeCastExpression) expression;
            final PsiExpression operand = typeCast.getOperand();
            return expressionAssignsVariableOrFails(operand, variable,
                    checkedMethods, strict);
        } else if(expression instanceof PsiArrayAccessExpression){
            final PsiArrayAccessExpression accessExpression =
                    (PsiArrayAccessExpression) expression;
            final PsiExpression arrayExpression =
                    accessExpression.getArrayExpression();
            final PsiExpression indexExpression =
                    accessExpression.getIndexExpression();
            return expressionAssignsVariableOrFails(arrayExpression, variable,
                    checkedMethods, strict) ||
                    expressionAssignsVariableOrFails(indexExpression, variable,
                            checkedMethods, strict);
        } else if(expression instanceof PsiPrefixExpression){
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression) expression;
            final PsiExpression operand = prefixExpression.getOperand();
            return expressionAssignsVariableOrFails(operand, variable,
                    checkedMethods, strict);
        } else if(expression instanceof PsiPostfixExpression){
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression) expression;
            final PsiExpression operand = postfixExpression.getOperand();
            return expressionAssignsVariableOrFails(operand, variable,
                    checkedMethods, strict);
        } else if(expression instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            return expressionAssignsVariableOrFails(lhs, variable,
                    checkedMethods, strict) ||
                    expressionAssignsVariableOrFails(rhs, variable,
                            checkedMethods, strict);
        } else if(expression instanceof PsiConditionalExpression){
            final PsiConditionalExpression conditional =
                    (PsiConditionalExpression) expression;
            final PsiExpression condition = conditional.getCondition();
            if(expressionAssignsVariableOrFails(condition, variable,
                    checkedMethods, strict)){
                return true;
            }
            final PsiExpression thenExpression =
                    conditional.getThenExpression();
            final PsiExpression elseExpression =
                    conditional.getElseExpression();
            return expressionAssignsVariableOrFails(thenExpression, variable,
                    checkedMethods, strict) &&
                    expressionAssignsVariableOrFails(elseExpression, variable,
                            checkedMethods, strict);
        } else if(expression instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression) expression;
            final PsiExpression lhs = assignment.getLExpression();
            if(expressionAssignsVariableOrFails(lhs, variable, checkedMethods,
                    strict)){
                return true;
            }
            final PsiExpression rhs = assignment.getRExpression();
            if(expressionAssignsVariableOrFails(rhs, variable, checkedMethods,
                    strict)){
                return true;
            }
            if(lhs instanceof PsiReferenceExpression){
                final PsiElement element = ((PsiReference) lhs).resolve();
                if(element != null && element.equals(variable)){
                    return true;
                }
            }
            return false;
        } else{
            return false;
        }
    }

    private static boolean newExpressionAssignsVariableOrFails(
            @NotNull PsiNewExpression newExpression, PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods, boolean strict){
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if(argumentList != null){
            final PsiExpression[] args = argumentList.getExpressions();
            for(final PsiExpression arg : args){
                if(expressionAssignsVariableOrFails(arg, variable,
                        checkedMethods, strict)){
                    return true;
                }
            }
        }
        final PsiArrayInitializerExpression arrayInitializer =
                newExpression.getArrayInitializer();
        if(expressionAssignsVariableOrFails(arrayInitializer, variable,
                checkedMethods, strict)){
            return true;
        }
        final PsiExpression[] arrayDimensions =
                newExpression.getArrayDimensions();
        for(final PsiExpression dim : arrayDimensions){
            if(expressionAssignsVariableOrFails(dim, variable,
                    checkedMethods, strict)){
                return true;
            }
        }
        return false;
    }

    private static boolean methodCallAssignsVariableOrFails(
            @NotNull PsiMethodCallExpression callExpression,
            PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods, boolean strict){
        final PsiExpressionList argList = callExpression.getArgumentList();
        final PsiExpression[] args = argList.getExpressions();
        for(final PsiExpression arg : args){
            if(expressionAssignsVariableOrFails(arg, variable, checkedMethods,
                    strict)){
                return true;
            }
        }
        final PsiReferenceExpression methodExpression =
                callExpression.getMethodExpression();
        if(expressionAssignsVariableOrFails(methodExpression, variable,
                checkedMethods, strict)){
            return true;
        }
        final PsiMethod method = callExpression.resolveMethod();
        if(method == null){
            return false;
        }
        final MethodSignature methodSignature =
                method.getSignature(PsiSubstitutor.EMPTY);
        if(!checkedMethods.add(methodSignature)){
            return false;
        }
        final PsiClass containingClass =
                ClassUtils.getContainingClass(callExpression);
        final PsiClass calledClass = method.getContainingClass();
        if(calledClass == null || !calledClass.equals(containingClass)){
            return false;
        }
        if(method.hasModifierProperty(PsiModifier.STATIC)
                || method.isConstructor()
                || method.hasModifierProperty(PsiModifier.PRIVATE)
                || method.hasModifierProperty(PsiModifier.FINAL)
                || calledClass.hasModifierProperty(PsiModifier.FINAL)){
            final PsiCodeBlock body = method.getBody();
            return blockAssignsVariableOrFails(body, variable,
                    checkedMethods, strict);
        }
        return false;
    }
}