/*
 * Copyright 2003-2005 Dave Griffith
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
import org.jetbrains.annotations.NotNull;

public class RecursionUtils{
    private RecursionUtils(){
        super();
    }

    public static boolean statementMayReturnBeforeRecursing(
            PsiStatement statement, PsiMethod method){
        if(statement == null){
            return true;
        }
        if(statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiThrowStatement ||
                statement instanceof PsiExpressionListStatement ||
                statement instanceof PsiExpressionStatement ||
                statement instanceof PsiEmptyStatement ||
                statement instanceof PsiAssertStatement ||
                statement instanceof PsiDeclarationStatement){
            return false;
        } else if(statement instanceof PsiReturnStatement){
            final PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if(returnValue != null){
                if(expressionMustRecurse(returnValue, method)){
                    return false;
                }
            }
            return true;
        } else if(statement instanceof PsiForStatement){
            return forStatementMayReturnBeforeRecursing(
                    (PsiForStatement) statement, method);
        } else if(statement instanceof PsiForeachStatement){
            return foreachStatementMayReturnBeforeRecursing(
                    (PsiForeachStatement) statement, method);
        } else if(statement instanceof PsiWhileStatement){
            return whileStatementMayReturnBeforeRecursing(
                    (PsiWhileStatement) statement, method);
        } else if(statement instanceof PsiDoWhileStatement){
            return doWhileStatementMayReturnBeforeRecursing(
                    (PsiDoWhileStatement) statement, method);
        } else if(statement instanceof PsiSynchronizedStatement){
            final PsiCodeBlock body = ((PsiSynchronizedStatement) statement)
                    .getBody();
            return codeBlockMayReturnBeforeRecursing(body, method, false);
        } else if(statement instanceof PsiBlockStatement){
            final PsiCodeBlock codeBlock = ((PsiBlockStatement) statement)
                    .getCodeBlock();
            return codeBlockMayReturnBeforeRecursing(codeBlock, method, false);
        } else if(statement instanceof PsiLabeledStatement){
            return labeledStatementMayReturnBeforeRecursing(
                    (PsiLabeledStatement) statement, method);
        } else if(statement instanceof PsiIfStatement){
            return ifStatementMayReturnBeforeRecursing(
                    (PsiIfStatement) statement, method);
        } else if(statement instanceof PsiTryStatement){
            return tryStatementMayReturnBeforeRecursing(
                    (PsiTryStatement) statement, method);
        } else if(statement instanceof PsiSwitchStatement){
            return switchStatementMayReturnBeforeRecursing(
                    (PsiSwitchStatement) statement, method);
        } else   // unknown statement type
        {
            return true;
        }
    }

    private static boolean doWhileStatementMayReturnBeforeRecursing(
            PsiDoWhileStatement loopStatement, PsiMethod method){
        final PsiStatement body = loopStatement.getBody();
        return statementMayReturnBeforeRecursing(body, method);
    }

    private static boolean whileStatementMayReturnBeforeRecursing(
            PsiWhileStatement loopStatement, PsiMethod method){
        final PsiExpression test = loopStatement.getCondition();
        if(expressionMustRecurse(test, method)){
            return false;
        }
        final PsiStatement body = loopStatement.getBody();
        return statementMayReturnBeforeRecursing(body, method);
    }

    private static boolean forStatementMayReturnBeforeRecursing(
            PsiForStatement loopStatement, PsiMethod method){
        final PsiStatement initialization = loopStatement.getInitialization();

        if(statementMayReturnBeforeRecursing(initialization, method)){
            return true;
        }
        final PsiExpression test = loopStatement.getCondition();
        if(expressionMustRecurse(test, method)){
            return false;
        }
        final PsiStatement body = loopStatement.getBody();
        return statementMayReturnBeforeRecursing(body, method);
    }

    private static boolean foreachStatementMayReturnBeforeRecursing(
            PsiForeachStatement loopStatement, PsiMethod method){
        final PsiExpression test = loopStatement.getIteratedValue();
        if(expressionMustRecurse(test, method)){
            return false;
        }
        final PsiStatement body = loopStatement.getBody();
        return statementMayReturnBeforeRecursing(body, method);
    }

    private static boolean switchStatementMayReturnBeforeRecursing(
            PsiSwitchStatement switchStatement, PsiMethod method){

        final PsiCodeBlock body = switchStatement.getBody();
        if(body == null){
            return true;
        }
        final PsiStatement[] statements = body.getStatements();
        for(final PsiStatement statement : statements){
            if(statementMayReturnBeforeRecursing(statement, method)){
                return true;
            }
        }
        return false;
    }

    private static boolean tryStatementMayReturnBeforeRecursing(
            PsiTryStatement tryStatement, PsiMethod method){
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if(finallyBlock != null){
            if(!codeBlockMayReturnBeforeRecursing(finallyBlock, method, false)){
                return false;
            }
        }
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if(codeBlockMayReturnBeforeRecursing(tryBlock, method, false)){
            return true;
        }
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for(final PsiCodeBlock catchBlock : catchBlocks){
            if(codeBlockMayReturnBeforeRecursing(catchBlock, method, false)){
                return true;
            }
        }
        return false;
    }

    private static boolean ifStatementMayReturnBeforeRecursing(
            PsiIfStatement ifStatement, PsiMethod method){
        final PsiExpression test = ifStatement.getCondition();
        if(expressionMustRecurse(test, method)){
            return false;
        }
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        if(statementMayReturnBeforeRecursing(thenBranch, method)){
            return true;
        }
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        return elseBranch != null &&
                statementMayReturnBeforeRecursing(elseBranch, method);
    }

    private static boolean labeledStatementMayReturnBeforeRecursing(
            PsiLabeledStatement labeledStatement, PsiMethod method){
        final PsiStatement statement = labeledStatement.getStatement();
        return statementMayReturnBeforeRecursing(statement, method);
    }

    private static boolean codeBlockMayReturnBeforeRecursing(PsiCodeBlock block,
                                                             PsiMethod method,
                                                             boolean endsInImplicitReturn){
        if(block == null){
            return true;
        }
        final PsiStatement[] statements = block.getStatements();
        for(final PsiStatement statement : statements){
            if(statementMayReturnBeforeRecursing(statement, method)){
                return true;
            }
            if(statementMustRecurse(statement, method)){
                return false;
            }
        }
        return endsInImplicitReturn;
    }

    public static boolean methodMayRecurse(@NotNull PsiMethod method){
        final RecursionVisitor recursionVisitor = new RecursionVisitor(method);
        method.accept(recursionVisitor);
        return recursionVisitor.isRecursive();
    }

    private static boolean expressionMustRecurse(PsiExpression exp,
                                                 PsiMethod method){
        if(exp == null){
            return false;
        }
        if(exp instanceof PsiMethodCallExpression){
            return methodCallExpressionMustRecurse(
                    (PsiMethodCallExpression) exp, method);
        }
        if(exp instanceof PsiNewExpression){
            return newExpressionMustRecurse((PsiNewExpression) exp, method);
        }
        if(exp instanceof PsiAssignmentExpression){
            return assignmentExpressionMustRecurse(
                    (PsiAssignmentExpression) exp, method);
        }
        if(exp instanceof PsiArrayInitializerExpression){
            return arrayInitializerExpressionMustRecurse(
                    (PsiArrayInitializerExpression) exp, method);
        }
        if(exp instanceof PsiTypeCastExpression){
            return typeCastExpressionMustRecurse((PsiTypeCastExpression) exp,
                                                 method);
        }
        if(exp instanceof PsiArrayAccessExpression){
            return arrayAccessExpressionMustRecurse(
                    (PsiArrayAccessExpression) exp, method);
        }
        if(exp instanceof PsiPrefixExpression){
            return prefixExpressionMustRecurse((PsiPrefixExpression) exp,
                                               method);
        }
        if(exp instanceof PsiPostfixExpression){
            return postfixExpressionMustRecurse((PsiPostfixExpression) exp,
                                                method);
        }
        if(exp instanceof PsiBinaryExpression){
            return binaryExpressionMustRecurse((PsiBinaryExpression) exp,
                                               method);
        }
        if(exp instanceof PsiInstanceOfExpression){
            return instanceOfExpressionMustRecurse(
                    (PsiInstanceOfExpression) exp, method);
        }
        if(exp instanceof PsiConditionalExpression){
            return conditionalExpressionMustRecurse(
                    (PsiConditionalExpression) exp, method);
        }
        if(exp instanceof PsiParenthesizedExpression){
            return parenthesizedExpressionMustRecurse(
                    (PsiParenthesizedExpression) exp, method);
        }
        if(exp instanceof PsiReferenceExpression){
            return referenceExpressionMustRecurse((PsiReferenceExpression) exp,
                                                  method);
        }
        if(exp instanceof PsiLiteralExpression ||
                exp instanceof PsiClassObjectAccessExpression ||
                exp instanceof PsiThisExpression ||
                exp instanceof PsiSuperExpression){
            return false;
        }
        return false;
    }

    private static boolean conditionalExpressionMustRecurse(
            PsiConditionalExpression expression, PsiMethod method){
        final PsiExpression condExpression = expression.getCondition();
        if(expressionMustRecurse(condExpression, method)){
            return true;
        }
        final PsiExpression thenExpression = expression.getThenExpression();
        final PsiExpression elseExpression = expression.getElseExpression();
        return expressionMustRecurse(thenExpression, method)
                && expressionMustRecurse(elseExpression, method);
    }

    private static boolean binaryExpressionMustRecurse(
            PsiBinaryExpression expression, PsiMethod method){
        final PsiExpression lhs = expression.getLOperand();
        if(expressionMustRecurse(lhs, method)){
            return true;
        }
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(tokenType.equals(JavaTokenType.ANDAND) ||
                tokenType.equals(JavaTokenType.OROR)){
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        return expressionMustRecurse(rhs, method);
    }

    private static boolean arrayAccessExpressionMustRecurse(
            PsiArrayAccessExpression expression, PsiMethod method){
        final PsiExpression arrayExp = expression.getArrayExpression();
        final PsiExpression indexExp = expression.getIndexExpression();
        return expressionMustRecurse(arrayExp, method) ||
                expressionMustRecurse(indexExp, method);
    }

    private static boolean arrayInitializerExpressionMustRecurse(
            PsiArrayInitializerExpression expression, PsiMethod method){
        final PsiExpression[] initializers = expression.getInitializers();
        for(final PsiExpression initializer : initializers){
            if(expressionMustRecurse(initializer, method)){
                return true;
            }
        }
        return false;
    }

    private static boolean prefixExpressionMustRecurse(
            PsiPrefixExpression expression, PsiMethod method){
        final PsiExpression operand = expression.getOperand();
        return expressionMustRecurse(operand, method);
    }

    private static boolean postfixExpressionMustRecurse(
            PsiPostfixExpression expression, PsiMethod method){
        final PsiExpression operand = expression.getOperand();
        return expressionMustRecurse(operand, method);
    }

    private static boolean instanceOfExpressionMustRecurse(
            PsiInstanceOfExpression expression, PsiMethod method){
        final PsiExpression operand = expression.getOperand();
        return expressionMustRecurse(operand, method);
    }

    private static boolean parenthesizedExpressionMustRecurse(
            PsiParenthesizedExpression expression, PsiMethod method){
        final PsiExpression innerExpression = expression.getExpression();
        return expressionMustRecurse(innerExpression, method);
    }

    private static boolean referenceExpressionMustRecurse(
            PsiReferenceExpression expression, PsiMethod method){

        final PsiExpression qualifierExpression = expression
                .getQualifierExpression();
        if(qualifierExpression != null){
            return expressionMustRecurse(qualifierExpression, method);
        }
        return false;
    }

    private static boolean typeCastExpressionMustRecurse(
            PsiTypeCastExpression expression, PsiMethod method){
        final PsiExpression operand = expression.getOperand();
        return expressionMustRecurse(operand, method);
    }

    private static boolean assignmentExpressionMustRecurse(
            PsiAssignmentExpression assignmentExpression, PsiMethod method){
        final PsiExpression rhs = assignmentExpression.getRExpression();
        final PsiExpression lhs = assignmentExpression.getLExpression();
        return expressionMustRecurse(rhs, method) ||
                expressionMustRecurse(lhs, method);
    }

    private static boolean newExpressionMustRecurse(PsiNewExpression exp,
                                                    PsiMethod method){
        final PsiExpression[] arrayDimensions = exp.getArrayDimensions();
        for(final PsiExpression arrayDimension : arrayDimensions){
            if(expressionMustRecurse(arrayDimension, method)){
                return true;
            }
        }
        final PsiArrayInitializerExpression arrayInitializer = exp
                .getArrayInitializer();
        if(expressionMustRecurse(arrayInitializer, method)){
            return true;
        }
        final PsiExpression qualifier = exp.getQualifier();
        if(expressionMustRecurse(qualifier, method)){
            return true;
        }
        final PsiExpressionList argumentList = exp.getArgumentList();
        if(argumentList != null){
            final PsiExpression[] args = argumentList.getExpressions();
            for(final PsiExpression arg : args){
                if(expressionMustRecurse(arg, method)){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean methodCallExpressionMustRecurse(
            PsiMethodCallExpression exp, PsiMethod method){
        final PsiReferenceExpression methodExpression = exp
                .getMethodExpression();
        if(methodExpression == null){
            return false;
        }
        final PsiMethod referencedMethod = exp.resolveMethod();
        if(referencedMethod == null){
            return false;
        }
        if(referencedMethod.equals(method)){
            if(method.hasModifierProperty(PsiModifier.STATIC) ||
                    method.hasModifierProperty(PsiModifier.PRIVATE)){
                return true;
            }
            final PsiExpression qualifier = methodExpression
                    .getQualifierExpression();
            if(qualifier == null || qualifier instanceof PsiThisExpression){
                return true;
            }
        }

        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if(expressionMustRecurse(qualifier, method)){
            return true;
        }
        final PsiExpressionList argumentList = exp.getArgumentList();
        if(argumentList != null){
            final PsiExpression[] args = argumentList.getExpressions();
            for(final PsiExpression arg : args){
                if(expressionMustRecurse(arg, method)){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean statementMustRecurse(PsiStatement statement,
                                                PsiMethod method){
        if(statement == null){
            return false;
        }
        if(statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiThrowStatement ||
                statement instanceof PsiEmptyStatement ||
                statement instanceof PsiAssertStatement){
            return false;
        } else if(statement instanceof PsiExpressionListStatement){
            final PsiExpressionList expressionList = ((PsiExpressionListStatement) statement)
                    .getExpressionList();
            if(expressionList == null){
                return false;
            }
            final PsiExpression[] expressions = expressionList.getExpressions();
            for(final PsiExpression expression : expressions){
                if(expressionMustRecurse(expression, method)){
                    return true;
                }
            }
            return false;
        } else if(statement instanceof PsiExpressionStatement){
            final PsiExpression expression = ((PsiExpressionStatement) statement)
                    .getExpression();
            return expressionMustRecurse(expression, method);
        } else if(statement instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement declaration = (PsiDeclarationStatement) statement;
            final PsiElement[] declaredElements = declaration
                    .getDeclaredElements();
            for(final PsiElement declaredElement : declaredElements){
                if(declaredElement instanceof PsiLocalVariable){
                    final PsiLocalVariable variable = (PsiLocalVariable) declaredElement;
                    final PsiExpression initializer = variable.getInitializer();
                    if(expressionMustRecurse(initializer, method)){
                        return true;
                    }
                }
            }
            return false;
        } else if(statement instanceof PsiReturnStatement){
            final PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if(returnValue != null){
                if(expressionMustRecurse(returnValue, method)){
                    return true;
                }
            }
            return false;
        } else if(statement instanceof PsiForStatement){
            return forStatementMustRecurse((PsiForStatement) statement, method);
        } else if(statement instanceof PsiForeachStatement){
            return foreachStatementMustRecurse((PsiForeachStatement) statement,
                                               method);
        } else if(statement instanceof PsiWhileStatement){
            return whileStatementMustRecurse((PsiWhileStatement) statement,
                                             method);
        } else if(statement instanceof PsiDoWhileStatement){
            return doWhileStatementMustRecurse((PsiDoWhileStatement) statement,
                                               method);
        } else if(statement instanceof PsiSynchronizedStatement){
            final PsiCodeBlock body = ((PsiSynchronizedStatement) statement)
                    .getBody();
            return codeBlockMustRecurse(body, method);
        } else if(statement instanceof PsiBlockStatement){
            final PsiCodeBlock codeBlock = ((PsiBlockStatement) statement)
                    .getCodeBlock();
            return codeBlockMustRecurse(codeBlock, method);
        } else if(statement instanceof PsiLabeledStatement){
            return labeledStatementMustRecurse((PsiLabeledStatement) statement,
                                               method);
        } else if(statement instanceof PsiIfStatement){
            return ifStatementMustRecurse((PsiIfStatement) statement, method);
        } else if(statement instanceof PsiTryStatement){
            return tryStatementMustRecurse((PsiTryStatement) statement, method);
        } else if(statement instanceof PsiSwitchStatement){
            return switchStatementMustRecurse((PsiSwitchStatement) statement,
                                              method);
        } else   // unknown statement type
        {
            return false;
        }
    }

    private static boolean switchStatementMustRecurse(
            PsiSwitchStatement switchStatement, PsiMethod method){
        final PsiExpression switchExpression = switchStatement.getExpression();
        return expressionMustRecurse(switchExpression, method);
    }

    private static boolean tryStatementMustRecurse(PsiTryStatement tryStatement,
                                                   PsiMethod method){
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if(codeBlockMustRecurse(tryBlock, method)){
            return true;
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        return codeBlockMustRecurse(finallyBlock, method);
    }

    private static boolean codeBlockMustRecurse(PsiCodeBlock block,
                                                PsiMethod method){
        if(block == null){
            return false;
        }
        final PsiStatement[] statements = block.getStatements();

        for(final PsiStatement statement : statements){
            if(statementMustRecurse(statement, method)){
                return true;
            }
        }
        return false;
    }

    private static boolean ifStatementMustRecurse(PsiIfStatement ifStatement,
                                                  PsiMethod method){
        final PsiExpression condition = ifStatement.getCondition();
        if(expressionMustRecurse(condition, method)){
            return true;
        }
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        if(thenBranch == null || elseBranch == null){
            return false;
        }
        return statementMustRecurse(thenBranch, method) &&
                statementMustRecurse(elseBranch, method);
    }

    private static boolean forStatementMustRecurse(PsiForStatement forStatement,
                                                   PsiMethod method){
        final PsiStatement initialization = forStatement.getInitialization();
        if(statementMustRecurse(initialization, method)){
            return true;
        }
        final PsiExpression condition = forStatement.getCondition();
        if(expressionMustRecurse(condition, method)){
            return true;
        }
        if(BoolUtils.isTrue(condition)){
            final PsiStatement body = forStatement.getBody();
            return statementMustRecurse(body, method);
        }
        return false;
    }

    private static boolean foreachStatementMustRecurse(
            PsiForeachStatement foreachStatement, PsiMethod method){
        final PsiExpression iteration = foreachStatement.getIteratedValue();
        return expressionMustRecurse(iteration, method);
    }

    private static boolean whileStatementMustRecurse(
            PsiWhileStatement whileStatement, PsiMethod method){

        final PsiExpression condition = whileStatement.getCondition();
        if(expressionMustRecurse(condition, method)){
            return true;
        }
        if(BoolUtils.isTrue(condition)){
            final PsiStatement body = whileStatement.getBody();
            return statementMustRecurse(body, method);
        }
        return false;
    }

    private static boolean doWhileStatementMustRecurse(
            PsiDoWhileStatement doWhileStatement, PsiMethod method){

        final PsiStatement body = doWhileStatement.getBody();
        if(statementMustRecurse(body, method)){
            return true;
        }
        final PsiExpression condition = doWhileStatement.getCondition();
        return expressionMustRecurse(condition, method);
    }

    private static boolean labeledStatementMustRecurse(
            PsiLabeledStatement labeledStatement, PsiMethod method){
        final PsiStatement body = labeledStatement.getStatement();
        return statementMustRecurse(body, method);
    }

    public static boolean methodMustRecurseBeforeReturning(
            @NotNull PsiMethod method){
        final PsiCodeBlock body = method.getBody();
        if(body == null){
            return false;
        }
        return !codeBlockMayReturnBeforeRecursing(body, method, true);
    }
}
