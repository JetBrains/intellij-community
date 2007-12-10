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
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Set;

class ExceptionUtils{

    private ExceptionUtils(){
        super();
    }

    public static Set<PsiType> getExceptionTypesHandled(
            PsiTryStatement statement){
        final Set<PsiType> out = new HashSet<PsiType>(10);
        final PsiParameter[] params = statement.getCatchBlockParameters();
        for(PsiParameter param : params){
            final PsiType type = param.getType();
            out.add(type);
        }
        return out;
    }

    private static void calculateExceptionsThrownForStatement(
            PsiStatement statement,
            Set<PsiType> exceptionTypes){
        if(statement == null){
            return;
        }
        if(statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement){
            // don't do anything
        } else if(statement instanceof PsiReturnStatement){
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement) statement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if(returnValue != null){
                calculateExceptionsThrown(returnValue, exceptionTypes);
            }
        } else if(statement instanceof PsiThrowStatement){
            final PsiThrowStatement throwStatement =
                    (PsiThrowStatement)statement;
            calculateExceptionsThrownForThrowStatement(throwStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiExpressionListStatement){
            final PsiExpressionListStatement expressionListStatement =
                    (PsiExpressionListStatement) statement;
            calculateExceptionsThrownForExpressionListStatement(
                    expressionListStatement, exceptionTypes);
        } else if(statement instanceof PsiExpressionStatement){
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement) statement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            calculateExceptionsThrown(expression, exceptionTypes);
        } else if(statement instanceof PsiAssertStatement){
            final PsiAssertStatement assertStatement =
                    (PsiAssertStatement) statement;
            calculateExceptionsThrownForAssertStatement(assertStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement) statement;
            calculateExceptionsThrownForDeclarationStatemt(declarationStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiForStatement){
            final PsiForStatement forStatement = (PsiForStatement)statement;
            calculateExceptionsThrownForForExpression(
                    forStatement, exceptionTypes);
        } else if(statement instanceof PsiWhileStatement){
            final PsiWhileStatement whileStatement =
                    (PsiWhileStatement)statement;
            calculateExceptionsThrownForWhileStatement(
                    whileStatement, exceptionTypes);
        } else if(statement instanceof PsiDoWhileStatement){
            final PsiDoWhileStatement doWhileStatement =
                    (PsiDoWhileStatement)statement;
            calculateExceptionsThrownForDoWhileStatement(doWhileStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiSynchronizedStatement){
            final PsiSynchronizedStatement synchronizedStatement =
                    (PsiSynchronizedStatement)statement;
            calculateExceptionsThrownForSynchronizedStatement(
                    synchronizedStatement, exceptionTypes);
        } else if(statement instanceof PsiBlockStatement){
            final PsiBlockStatement block = (PsiBlockStatement) statement;
            calculateExceptionsThrownForBlockStatement(block, exceptionTypes);
        } else if(statement instanceof PsiLabeledStatement){
            final PsiLabeledStatement labeledStatement =
                    (PsiLabeledStatement) statement;
            calculateExceptionsThrownForLabeledStatement(labeledStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiIfStatement){
            final PsiIfStatement ifStatement = (PsiIfStatement) statement;
            calculateExceptionsThrownForIfStatement(ifStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiTryStatement){
            final PsiTryStatement tryStatement = (PsiTryStatement) statement;
            calculateExceptionsThrownForTryStatement(tryStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiSwitchStatement){
            final PsiSwitchStatement switchStatement =
                    (PsiSwitchStatement) statement;
            calculateExceptionsThrownForSwitchStatement(switchStatement,
                    exceptionTypes);
        }
    }

    private static void calculateExceptionsThrownForLabeledStatement(
            PsiLabeledStatement labeledStatement,
            Set<PsiType> exceptionTypes){
        final PsiStatement statement = labeledStatement.getStatement();
        calculateExceptionsThrownForStatement(statement, exceptionTypes);
    }

    private static void calculateExceptionsThrownForExpressionListStatement(
            PsiExpressionListStatement listStatement,
            Set<PsiType> exceptionTypes){
        final PsiExpressionList expressionList =
                listStatement.getExpressionList();
        final PsiExpression[] expressions = expressionList.getExpressions();
        for(PsiExpression expression : expressions){
            calculateExceptionsThrown(expression, exceptionTypes);
        }
    }

    private static void calculateExceptionsThrownForDeclarationStatemt(
            PsiDeclarationStatement declarationStatement,
            Set<PsiType> exceptionTypes){
        final PsiElement[] elements =
                declarationStatement.getDeclaredElements();
        for(PsiElement element : elements){
            final PsiVariable var = (PsiVariable) element;
            final PsiExpression initializer = var.getInitializer();
            if(initializer != null){
                calculateExceptionsThrown(initializer, exceptionTypes);
            }
        }
    }

    private static void calculateExceptionsThrownForAssertStatement(
            PsiAssertStatement assertStatement,
            Set<PsiType> exceptionTypes) {
        final PsiExpression assertCondition =
                assertStatement.getAssertCondition();
        calculateExceptionsThrown(assertCondition, exceptionTypes);
        final PsiExpression assertDescription =
                assertStatement.getAssertDescription();
        calculateExceptionsThrown(assertDescription, exceptionTypes);
    }

    private static void calculateExceptionsThrownForThrowStatement(
            PsiThrowStatement throwStatement,
            Set<PsiType> exceptionTypes){
        final PsiExpression exception = throwStatement.getException();
        if(exception == null){
            return;
        }
        final PsiType type = exception.getType();
        if (type != null) {
            exceptionTypes.add(type);
        }
        calculateExceptionsThrown(exception, exceptionTypes);
    }

    private static void calculateExceptionsThrownForSwitchStatement(
            PsiSwitchStatement switchStatement,
            Set<PsiType> exceptionTypes){
        final PsiExpression switchExpression = switchStatement.getExpression();
        calculateExceptionsThrown(switchExpression, exceptionTypes);
        final PsiCodeBlock body = switchStatement.getBody();
        calculateExceptionsThrownForCodeBlock(body, exceptionTypes);
    }

    private static void calculateExceptionsThrownForTryStatement(
            PsiTryStatement tryStatement,
            Set<PsiType> exceptionTypes){
        final Set<PsiType> exceptionThrown = new HashSet<PsiType>(10);
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        calculateExceptionsThrownForCodeBlock(tryBlock, exceptionThrown);
        final Set<PsiType> exceptionHandled =
                getExceptionTypesHandled(tryStatement);
        for(PsiType thrownType : exceptionThrown){
            boolean found = false;
            for(PsiType handledType : exceptionHandled){
                if(handledType.isAssignableFrom(thrownType)){
                    found = true;
                    break;
                }
            }
            if(!found){
                exceptionTypes.add(thrownType);
            }
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if(finallyBlock != null){
            calculateExceptionsThrownForCodeBlock(finallyBlock, exceptionTypes);
        }
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for(PsiCodeBlock catchBlock : catchBlocks){
            calculateExceptionsThrownForCodeBlock(catchBlock, exceptionTypes);
        }
    }

    private static void calculateExceptionsThrownForIfStatement(
            PsiIfStatement ifStatement,
            Set<PsiType> exceptionTypes){
        final PsiExpression condition = ifStatement.getCondition();
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        calculateExceptionsThrown(condition, exceptionTypes);
        calculateExceptionsThrownForStatement(thenBranch, exceptionTypes);
        calculateExceptionsThrownForStatement(elseBranch, exceptionTypes);
    }

    private static void calculateExceptionsThrownForBlockStatement(
            PsiBlockStatement block,
            Set<PsiType> exceptionTypes){
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        calculateExceptionsThrownForCodeBlock(codeBlock, exceptionTypes);
    }

    private static void calculateExceptionsThrownForSynchronizedStatement(
            PsiSynchronizedStatement synchronizedStatement,
            Set<PsiType> exceptionTypes){
        final PsiExpression lockExpression =
                synchronizedStatement.getLockExpression();
        if(lockExpression != null){
            calculateExceptionsThrown(lockExpression, exceptionTypes);
        }
        final PsiCodeBlock body = synchronizedStatement.getBody();
        calculateExceptionsThrownForCodeBlock(body, exceptionTypes);
    }

    private static void calculateExceptionsThrownForDoWhileStatement(
            PsiDoWhileStatement loopStatement,
            Set<PsiType> exceptionTypes){
        final PsiExpression condition = loopStatement.getCondition();
        calculateExceptionsThrown(condition, exceptionTypes);
        final PsiStatement body = loopStatement.getBody();
        calculateExceptionsThrownForStatement(body, exceptionTypes);
    }

    private static void calculateExceptionsThrownForWhileStatement(
            PsiWhileStatement loopStatement,
            Set<PsiType> exceptionTypes){
        final PsiExpression condition = loopStatement.getCondition();
        calculateExceptionsThrown(condition, exceptionTypes);
        final PsiStatement body = loopStatement.getBody();
        calculateExceptionsThrownForStatement(body, exceptionTypes);
    }

    private static void calculateExceptionsThrownForForExpression(
            PsiForStatement loopStatement,
            Set<PsiType> exceptionTypes){
        final PsiStatement initialization = loopStatement.getInitialization();
        final PsiExpression condition = loopStatement.getCondition();
        final PsiStatement update = loopStatement.getUpdate();
        final PsiStatement body = loopStatement.getBody();
        calculateExceptionsThrownForStatement(initialization, exceptionTypes);
        calculateExceptionsThrown(condition, exceptionTypes);
        calculateExceptionsThrownForStatement(update, exceptionTypes);
        calculateExceptionsThrownForStatement(body, exceptionTypes);
    }

    private static void calculateExceptionsThrown(PsiExpression expression,
                                                  Set<PsiType> exceptionTypes){
        if(expression == null){
            return;
        }
        if(expression instanceof PsiThisExpression ||
                expression instanceof PsiLiteralExpression ||
                expression instanceof PsiSuperExpression ||
                expression instanceof PsiClassObjectAccessExpression){
        } else if(expression instanceof PsiTypeCastExpression){
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression)expression;
            calculateExceptionsThrownForTypeCast(typeCastExpression,
                    exceptionTypes);
        } else if(expression instanceof PsiInstanceOfExpression){
            final PsiInstanceOfExpression instanceOfExpression =
                    (PsiInstanceOfExpression)expression;
            calculateExceptionsThrownForInstanceOf(instanceOfExpression,
                    exceptionTypes);
        } else if(expression instanceof PsiReferenceExpression){
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) expression;
            final PsiExpression qualifier =
                    referenceExpression.getQualifierExpression();
            if(qualifier != null){
                calculateExceptionsThrown(qualifier, exceptionTypes);
            }
        } else if(expression instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)expression;
            calculateExceptionsThrownForMethodCall(methodCallExpression,
                    exceptionTypes);
        } else if(expression instanceof PsiNewExpression){
            final PsiNewExpression newExpression = (PsiNewExpression)expression;
            calculateExceptionsThrownForNewExpression(newExpression,
                    exceptionTypes);
        } else if(expression instanceof PsiArrayInitializerExpression){
            final PsiArrayInitializerExpression arrayInitializerExpression =
                    (PsiArrayInitializerExpression)expression;
            calculateExceptionsThrownForArrayInitializerExpression(
                    arrayInitializerExpression, exceptionTypes);
        } else if(expression instanceof PsiArrayAccessExpression){
            final PsiArrayAccessExpression arrayAccessExpression =
                    (PsiArrayAccessExpression)expression;
            calculateExceptionsThrownForArrayAccessExpression(
                    arrayAccessExpression, exceptionTypes);
        } else if(expression instanceof PsiPrefixExpression){
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)expression;
            calculateExceptionsThrownForPrefixException(prefixExpression,
                    exceptionTypes);
        } else if(expression instanceof PsiPostfixExpression){
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression)expression;
            calculateExceptionsThrownForPostixExpression(postfixExpression,
                    exceptionTypes);
        } else if(expression instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            calculateExceptionsThrownForBinaryExpression(binaryExpression,
                    exceptionTypes);
        } else if(expression instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression)expression;
            calculateExceptionsThrownForAssignmentExpression(
                    assignmentExpression, exceptionTypes);
        } else if(expression instanceof PsiConditionalExpression){
            final PsiConditionalExpression conditionalExpression =
                    (PsiConditionalExpression)expression;
            calculateExceptionsThrownForConditionalExcpression(
                    conditionalExpression, exceptionTypes);
        }
    }

    private static void calculateExceptionsThrownForTypeCast(
            PsiTypeCastExpression typeCastExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpression operand = typeCastExpression.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes);
    }

    private static void calculateExceptionsThrownForInstanceOf(
            PsiInstanceOfExpression instanceOfExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpression operand = instanceOfExpression.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes);
    }

    private static void calculateExceptionsThrownForNewExpression(
            PsiNewExpression newExpression, Set<PsiType> exceptionTypes){
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if(argumentList != null){
            final PsiExpression[] args = argumentList.getExpressions();
            for(PsiExpression arg : args){
                calculateExceptionsThrown(arg, exceptionTypes);
            }
        }
        final PsiExpression[] arrayDims = newExpression.getArrayDimensions();
        for(PsiExpression arrayDim : arrayDims){
            calculateExceptionsThrown(arrayDim, exceptionTypes);
        }
        final PsiExpression qualifier = newExpression.getQualifier();
        calculateExceptionsThrown(qualifier, exceptionTypes);
        final PsiArrayInitializerExpression arrayInitializer =
                newExpression.getArrayInitializer();
        calculateExceptionsThrown(arrayInitializer, exceptionTypes);
        final PsiMethod method = newExpression.resolveMethod();
        if(method != null){
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] list =
                    throwsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement referenceElement : list){
                final PsiClass exceptionClass =
                        (PsiClass) referenceElement.resolve();
                if(exceptionClass != null){
                    final PsiManager psiManager = exceptionClass.getManager();
                  final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
                    final PsiClassType exceptionType =
                            factory.createType(exceptionClass);
                    exceptionTypes.add(exceptionType);
                }
            }
        }
    }

    private static void calculateExceptionsThrownForMethodCall(
            PsiMethodCallExpression methodCallExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpressionList argumentList =
                methodCallExpression.getArgumentList();
        final PsiExpression[] expressions = argumentList.getExpressions();
        for(PsiExpression expression : expressions){
            calculateExceptionsThrown(expression, exceptionTypes);
        }
        final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
        calculateExceptionsThrown(methodExpression, exceptionTypes);
        final PsiMethod method = methodCallExpression.resolveMethod();
        if(method != null){
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] list =
                    throwsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement referenceElement : list){
                final PsiClass exceptionClass =
                        (PsiClass) referenceElement.resolve();
                if(exceptionClass != null){
                    final PsiManager psiManager = exceptionClass.getManager();
                  final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
                    final PsiClassType exceptionType =
                            factory.createType(exceptionClass);
                    exceptionTypes.add(exceptionType);
                }
            }
        }
    }

    private static void calculateExceptionsThrownForConditionalExcpression(
            PsiConditionalExpression conditionalExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpression condition = conditionalExpression.getCondition();
        final PsiExpression elseExpression =
                conditionalExpression.getElseExpression();
        final PsiExpression thenExpression =
                conditionalExpression.getThenExpression();
        calculateExceptionsThrown(condition, exceptionTypes);
        calculateExceptionsThrown(elseExpression, exceptionTypes);
        calculateExceptionsThrown(thenExpression, exceptionTypes);
    }

    private static void calculateExceptionsThrownForBinaryExpression(
            PsiBinaryExpression binaryExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpression lOperand = binaryExpression.getLOperand();
        calculateExceptionsThrown(lOperand, exceptionTypes);
        final PsiExpression rhs = binaryExpression.getROperand();
        calculateExceptionsThrown(rhs, exceptionTypes);
    }

    private static void calculateExceptionsThrownForAssignmentExpression(
            PsiAssignmentExpression assignmentExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpression lOperand = assignmentExpression.getLExpression();
        calculateExceptionsThrown(lOperand, exceptionTypes);
        final PsiExpression rhs = assignmentExpression.getRExpression();
        calculateExceptionsThrown(rhs, exceptionTypes);
    }

    private static void calculateExceptionsThrownForArrayInitializerExpression(
            PsiArrayInitializerExpression arrayInitializerExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpression[] initializers =
                arrayInitializerExpression.getInitializers();
        for(PsiExpression initializer : initializers){
            calculateExceptionsThrown(initializer, exceptionTypes);
        }
    }

    private static void calculateExceptionsThrownForArrayAccessExpression(
            PsiArrayAccessExpression arrayAccessExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpression arrayExpression =
                arrayAccessExpression.getArrayExpression();
        calculateExceptionsThrown(arrayExpression, exceptionTypes);
        final PsiExpression indexExpression =
                arrayAccessExpression.getIndexExpression();
        calculateExceptionsThrown(indexExpression, exceptionTypes);
    }

    private static void calculateExceptionsThrownForPrefixException(
            PsiPrefixExpression prefixExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpression operand = prefixExpression.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes);
    }

    private static void calculateExceptionsThrownForPostixExpression(
            PsiPostfixExpression postfixExpression,
            Set<PsiType> exceptionTypes){
        final PsiExpression operand = postfixExpression.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes);
    }

    public static void calculateExceptionsThrownForCodeBlock(
            PsiCodeBlock codeBlock, Set<PsiType> exceptionTypes){
        if(codeBlock == null){
            return;
        }
        final PsiStatement[] statements = codeBlock.getStatements();
        for(PsiStatement statement : statements){
            calculateExceptionsThrownForStatement(statement, exceptionTypes);
        }
    }
}