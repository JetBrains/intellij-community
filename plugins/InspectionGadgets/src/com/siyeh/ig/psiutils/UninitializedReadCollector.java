/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

public class UninitializedReadCollector {

    // Set to prevent duplicates
    private final Set<PsiExpression> uninitializedReads;

    public UninitializedReadCollector(){
        super();
        uninitializedReads = new HashSet<PsiExpression>();
    }

    public PsiExpression[] getUninitializedReads(){
        final PsiExpression[] array =
                new PsiExpression[uninitializedReads.size()];
        return uninitializedReads.toArray(array);
    }

    public boolean blockAssignsVariable(@Nullable PsiCodeBlock block,
                                        @NotNull PsiVariable variable){
        return blockAssignsVariable(block, variable,
                                    new HashSet<MethodSignature>());
    }

    private boolean blockAssignsVariable(
            @Nullable PsiCodeBlock block, @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        if(block == null){
            return false;
        }
        final PsiStatement[] statements = block.getStatements();
        for(final PsiStatement statement : statements){
            if(statementAssignsVariable(statement, variable, checkedMethods)){
                return true;
            }
        }
        return false;
    }

    private boolean statementAssignsVariable(
            @Nullable PsiStatement statement, @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
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
            //final PsiReturnStatement returnStatement =
            //        (PsiReturnStatement) statement;
            //final PsiExpression returnValue = returnStatement.getReturnValue();
            //return expressionAssignsVariable(returnValue, variable,
            //                                 checkedMethods);
            return true;
        } else if(statement instanceof PsiThrowStatement){
            final PsiThrowStatement throwStatement =
                    (PsiThrowStatement) statement;
            final PsiExpression exception = throwStatement.getException();
            return expressionAssignsVariable(exception, variable,
                                             checkedMethods);
        } else if(statement instanceof PsiExpressionListStatement){
            final PsiExpressionListStatement list =
                    (PsiExpressionListStatement) statement;
            final PsiExpressionList expressionList = list.getExpressionList();
            final PsiExpression[] expressions = expressionList.getExpressions();
            for(final PsiExpression expression : expressions){
                if(expressionAssignsVariable(expression, variable,
                                             checkedMethods)){
                    return true;
                }
            }
            return false;
        } else if(statement instanceof PsiExpressionStatement){
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement) statement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            return expressionAssignsVariable(expression, variable,
                    checkedMethods);
        } else if (statement instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement)statement;
            return declarationStatementAssignsVariable(declarationStatement,
                    variable, checkedMethods);
        } else if (statement instanceof PsiForStatement){
            final PsiForStatement forStatement = (PsiForStatement)statement;
            return forStatementAssignsVariable(forStatement, variable,
                    checkedMethods);
        } else if(statement instanceof PsiForeachStatement){
            final PsiForeachStatement foreachStatement =
                    (PsiForeachStatement)statement;
            return foreachStatementAssignsVariable(foreachStatement, variable);
        } else if(statement instanceof PsiWhileStatement){
            final PsiWhileStatement whileStatement =
                    (PsiWhileStatement) statement;
            return whileStatementAssignsVariable(whileStatement, variable,
                                                 checkedMethods);
        } else if(statement instanceof PsiDoWhileStatement) {
            final PsiDoWhileStatement doWhileStatement =
                    (PsiDoWhileStatement)statement;
            return doWhileAssignsVariable(doWhileStatement, variable,
                    checkedMethods);
        } else if(statement instanceof PsiSynchronizedStatement){
            final PsiSynchronizedStatement synchronizedStatement =
                    (PsiSynchronizedStatement)statement;
            final PsiCodeBlock body = synchronizedStatement.getBody();
            return blockAssignsVariable(body, variable, checkedMethods);
        } else if(statement instanceof PsiBlockStatement){
            final PsiBlockStatement blockStatement =
                    (PsiBlockStatement) statement;
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            return blockAssignsVariable(codeBlock, variable, checkedMethods);
        } else if (statement instanceof PsiLabeledStatement){
            final PsiLabeledStatement labeledStatement =
                    (PsiLabeledStatement) statement;
            final PsiStatement statementLabeled =
                    labeledStatement.getStatement();
            return statementAssignsVariable(statementLabeled, variable,
                                            checkedMethods);
        } else if(statement instanceof PsiIfStatement){
            final PsiIfStatement ifStatement = (PsiIfStatement) statement;
            return ifStatementAssignsVariable(ifStatement, variable,
                                              checkedMethods);
        } else if(statement instanceof PsiTryStatement){
            final PsiTryStatement tryStatement = (PsiTryStatement) statement;
            return tryStatementAssignsVariable(tryStatement, variable,
                                               checkedMethods);
        } else if(statement instanceof PsiSwitchStatement){
            final PsiSwitchStatement switchStatement =
                    (PsiSwitchStatement) statement;
            return switchStatementAssignsVariable(switchStatement, variable,
                                                  checkedMethods);
        } else{
            // unknown statement type
            return false;
        }
    }

    private boolean switchStatementAssignsVariable(
            @NotNull PsiSwitchStatement switchStatement,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods) {
        final PsiExpression expression = switchStatement.getExpression();
        if (expressionAssignsVariable(expression, variable, checkedMethods)) {
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
                assigns |= statementAssignsVariable(statement, variable,
                                                    checkedMethods);
                if (i == statements.length - 1 && !assigns) {
                    return false;
                }
            }
        }
        return containsDefault;
    }

    private boolean declarationStatementAssignsVariable(
            @NotNull PsiDeclarationStatement declarationStatement,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        final PsiElement[] elements =
                declarationStatement.getDeclaredElements();
        for(PsiElement element : elements){
            if(element instanceof PsiVariable){
                final PsiVariable variableElement = (PsiVariable) element;
                final PsiExpression initializer =
                        variableElement.getInitializer();
                if(expressionAssignsVariable(initializer, variable,
                                             checkedMethods)){
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryStatementAssignsVariable(
            @NotNull PsiTryStatement tryStatement,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        boolean initializedInTryOrCatch =
                blockAssignsVariable(tryBlock, variable, checkedMethods);
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for (final PsiCodeBlock catchBlock : catchBlocks){
            initializedInTryOrCatch &=
                    blockAssignsVariable(catchBlock, variable,
                                         checkedMethods);
        }
        if (initializedInTryOrCatch) {
            return true;
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        return blockAssignsVariable(finallyBlock, variable,
                                    checkedMethods);
    }

    private boolean ifStatementAssignsVariable(
            @NotNull PsiIfStatement ifStatement, @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        final PsiExpression condition = ifStatement.getCondition();
        if(expressionAssignsVariable(condition, variable, checkedMethods)){
            return true;
        }
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        return statementAssignsVariable(thenBranch, variable, checkedMethods) &&
                statementAssignsVariable(elseBranch, variable, checkedMethods);
    }

    private boolean doWhileAssignsVariable(
            @NotNull PsiDoWhileStatement doWhileStatement,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        final PsiExpression condition = doWhileStatement.getCondition();
        final PsiStatement body = doWhileStatement.getBody();
        return expressionAssignsVariable(condition, variable, checkedMethods)
                || statementAssignsVariable(body, variable, checkedMethods);
    }

    private boolean whileStatementAssignsVariable(
            @NotNull PsiWhileStatement whileStatement,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        final PsiExpression condition = whileStatement.getCondition();
        if(expressionAssignsVariable(condition, variable, checkedMethods)){
            return true;
        }
        if(BoolUtils.isTrue(condition)){
            final PsiStatement body = whileStatement.getBody();
            if(statementAssignsVariable(body, variable, checkedMethods)){
                return true;
            }
        }
        return false;
    }

    private boolean forStatementAssignsVariable(
            @NotNull PsiForStatement forStatement,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        final PsiStatement initialization = forStatement.getInitialization();
        if(statementAssignsVariable(initialization, variable, checkedMethods)){
            return true;
        }
        final PsiExpression condition = forStatement.getCondition();
        if(expressionAssignsVariable(condition, variable, checkedMethods)){
            return true;
        }
        if(BoolUtils.isTrue(condition)){
            final PsiStatement body = forStatement.getBody();
            if(statementAssignsVariable(body, variable, checkedMethods)){
                return true;
            }
            final PsiStatement update = forStatement.getUpdate();
            if(statementAssignsVariable(update, variable, checkedMethods)){
                return true;
            }
        }
        return false;
    }

    private boolean foreachStatementAssignsVariable(
            PsiForeachStatement forStatement, PsiVariable variable) {
        return false;
    }

    private boolean expressionAssignsVariable(
            @Nullable PsiExpression expression, @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        if(expression == null){
            return false;
        }
        if(expression instanceof PsiThisExpression ||
                expression instanceof PsiLiteralExpression ||
                expression instanceof PsiSuperExpression ||
                expression instanceof PsiClassObjectAccessExpression){
            return false;
        } else if(expression instanceof PsiReferenceExpression){
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) expression;
            return referenceExpressionAssignsVariable(referenceExpression,
                                                      variable,
                                                      checkedMethods);
        } else if(expression instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression)expression;
            return methodCallAssignsVariable(callExpression, variable,
                                             checkedMethods);
        } else if(expression instanceof PsiNewExpression){
            final PsiNewExpression newExpression = (PsiNewExpression)expression;
            return newExpressionAssignsVariable(newExpression, variable,
                                                checkedMethods);
        } else if(expression instanceof PsiArrayInitializerExpression){
            final PsiArrayInitializerExpression array =
                    (PsiArrayInitializerExpression) expression;
            final PsiExpression[] initializers = array.getInitializers();
            for(final PsiExpression initializer : initializers){
                if(expressionAssignsVariable(initializer, variable,
                                             checkedMethods)){
                    return true;
                }
            }
            return false;
        } else if(expression instanceof PsiTypeCastExpression){
            final PsiTypeCastExpression typeCast =
                    (PsiTypeCastExpression) expression;
            final PsiExpression operand = typeCast.getOperand();
            return expressionAssignsVariable(operand, variable, checkedMethods);
        } else if(expression instanceof PsiArrayAccessExpression){
            final PsiArrayAccessExpression accessExpression =
                    (PsiArrayAccessExpression) expression;
            final PsiExpression arrayExpression =
                    accessExpression.getArrayExpression();
            final PsiExpression indexExpression =
                    accessExpression.getIndexExpression();
            return expressionAssignsVariable(arrayExpression, variable,
                                             checkedMethods) ||
                    expressionAssignsVariable(indexExpression, variable,
                            checkedMethods);
        } else if(expression instanceof PsiPrefixExpression){
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression) expression;
            final PsiExpression operand = prefixExpression.getOperand();
            return expressionAssignsVariable(operand, variable, checkedMethods);
        } else if(expression instanceof PsiPostfixExpression){
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression) expression;
            final PsiExpression operand = postfixExpression.getOperand();
            return expressionAssignsVariable(operand, variable, checkedMethods);
        } else if(expression instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            return expressionAssignsVariable(lhs, variable, checkedMethods) ||
                    expressionAssignsVariable(rhs, variable, checkedMethods);
        } else if(expression instanceof PsiConditionalExpression){
            final PsiConditionalExpression conditional =
                    (PsiConditionalExpression) expression;
            final PsiExpression condition = conditional.getCondition();
            if(expressionAssignsVariable(condition, variable, checkedMethods)){
                return true;
            }
            final PsiExpression thenExpression =
                    conditional.getThenExpression();
            final PsiExpression elseExpression =
                    conditional.getElseExpression();
            return expressionAssignsVariable(thenExpression, variable,
                                             checkedMethods)
                    && expressionAssignsVariable(elseExpression, variable,
                                                 checkedMethods);
        } else if(expression instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression) expression;
            return assignmentExpressonAssignsVariable(assignment, variable,
                                                      checkedMethods);
        } else{
            return false;
        }
    }

    private boolean assignmentExpressonAssignsVariable(
            @NotNull PsiAssignmentExpression assignment,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods) {
        final PsiExpression lhs = assignment.getLExpression();
        if(expressionAssignsVariable(lhs, variable, checkedMethods)){
            return true;
        }
        final PsiExpression rhs = assignment.getRExpression();
        if(expressionAssignsVariable(rhs, variable, checkedMethods)){
            return true;
        }
        if(lhs instanceof PsiReferenceExpression){
            final PsiElement element = ((PsiReference) lhs).resolve();
            if(element != null && element.equals(variable)){
                return true;
            }
        }
        return false;
    }

    private boolean referenceExpressionAssignsVariable(
            @NotNull PsiReferenceExpression referenceExpression,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods) {
        final PsiExpression qualifierExpression =
                referenceExpression.getQualifierExpression();
        if(variable.equals(referenceExpression.resolve())){
            final PsiElement parent = referenceExpression.getParent();
            if(parent instanceof PsiAssignmentExpression){
                final PsiAssignmentExpression assignmentExpression =
                        (PsiAssignmentExpression)parent;
                final PsiExpression rhs =
                        assignmentExpression.getRExpression();
                if (rhs != null && rhs.equals(referenceExpression)) {
                    if (!referenceExpression.isQualified() ||
                            qualifierExpression instanceof PsiThisExpression) {
                        uninitializedReads.add(referenceExpression);
                    }
                }
            } else if (!referenceExpression.isQualified() ||
                    qualifierExpression instanceof PsiThisExpression) {
                uninitializedReads.add(referenceExpression);
            }
        }
        if(referenceExpression.isQualified()){
            return expressionAssignsVariable(qualifierExpression, variable,
                    checkedMethods);
        } else{
            return false;
        }
    }

    private boolean newExpressionAssignsVariable(
            @NotNull PsiNewExpression newExpression,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if(argumentList != null){
            final PsiExpression[] args = argumentList.getExpressions();
            for(final PsiExpression arg : args){
                if(expressionAssignsVariable(arg, variable, checkedMethods)){
                    return true;
                }
            }
        }
        final PsiArrayInitializerExpression arrayInitializer =
                newExpression.getArrayInitializer();
        if(expressionAssignsVariable(arrayInitializer, variable,
                                     checkedMethods)){
            return true;
        }
        final PsiExpression[] arrayDimensions =
                newExpression.getArrayDimensions();
        for(final PsiExpression dim : arrayDimensions){
            if(expressionAssignsVariable(dim, variable, checkedMethods)){
                return true;
            }
        }
        return false;
    }

    private boolean methodCallAssignsVariable(
            @NotNull PsiMethodCallExpression callExpression,
            @NotNull PsiVariable variable,
            @NotNull Set<MethodSignature> checkedMethods){
        final PsiExpressionList argumentList = callExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        for(final PsiExpression argument : arguments){
            if(expressionAssignsVariable(argument, variable,
                    checkedMethods)){
                return true;
            }
        }
        final PsiReferenceExpression methodExpression =
                callExpression.getMethodExpression();
        if(expressionAssignsVariable(methodExpression, variable,
                                     checkedMethods)) {
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

        // Can remark out this block to continue chase outside of of
        // current class
        if(!calledClass.equals(containingClass)){
            return false;
        }

        if(method.hasModifierProperty(PsiModifier.STATIC)
                || method.isConstructor()
                || method.hasModifierProperty(PsiModifier.PRIVATE)
                || method.hasModifierProperty(PsiModifier.FINAL)
                || calledClass.hasModifierProperty(PsiModifier.FINAL)){
            final PsiCodeBlock body = method.getBody();
            return blockAssignsVariable(body, variable, checkedMethods);
        }
        return false;
    }
}