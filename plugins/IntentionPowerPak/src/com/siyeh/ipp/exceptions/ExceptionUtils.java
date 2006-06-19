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
            calculateExceptionsThrownForThrowStatement(
                    (PsiThrowStatement) statement, exceptionTypes);
        } else if(statement instanceof PsiExpressionListStatement){
            final PsiExpressionListStatement listStatement =
                    (PsiExpressionListStatement) statement;
            calculateExceptionsThrownForExpressionListStatement(listStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiExpressionStatement){
            final PsiExpressionStatement expStatement =
                    (PsiExpressionStatement) statement;
            final PsiExpression expression = expStatement.getExpression();
            calculateExceptionsThrown(expression, exceptionTypes);
        } else if(statement instanceof PsiAssertStatement){
            final PsiAssertStatement assertStatement =
                    (PsiAssertStatement) statement;
            calculateExceptionsThrownForAssertStatement(assertStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement declStatement =
                    (PsiDeclarationStatement) statement;
            calculateExceptionsThrownForDeclarationStatemt(declStatement,
                    exceptionTypes);
        } else if(statement instanceof PsiForStatement){
            calculateExceptionsThrownForForExpression(
                    (PsiForStatement) statement,
                    exceptionTypes);
        } else if(statement instanceof PsiWhileStatement){
            calculateExceptionsThrownForWhileStatement(
                    (PsiWhileStatement) statement,
                    exceptionTypes);
        } else if(statement instanceof PsiDoWhileStatement){
            calculateExceptionsThrownForDoWhileStatement(
                    (PsiDoWhileStatement) statement,
                    exceptionTypes);
        } else if(statement instanceof PsiSynchronizedStatement){
            calculateExceptionsThrownForSynchronizedStatement(
                    (PsiSynchronizedStatement) statement,
                    exceptionTypes);
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
            PsiDeclarationStatement declStatement,
            Set<PsiType> exceptionTypes){
        final PsiElement[] elements = declStatement.getDeclaredElements();
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
            Set<PsiType> exceptionTypes
    ){
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
        exceptionTypes.add(type);
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
            Set<PsiType>exceptionTypes){
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
            calculateExceptionsThrownForCodeBlock(catchBlock,
                    exceptionTypes);
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
            PsiSynchronizedStatement syncStatement,
            Set<PsiType> exceptionTypes){
        final PsiExpression lockExpression = syncStatement.getLockExpression();
        if(lockExpression != null){
            calculateExceptionsThrown(lockExpression, exceptionTypes);
        }
        final PsiCodeBlock body = syncStatement.getBody();
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

    private static void calculateExceptionsThrown(PsiExpression exp,
                                                  Set<PsiType> exceptionTypes){
        if(exp == null){
            return;
        }
        if(exp instanceof PsiThisExpression ||
                exp instanceof PsiLiteralExpression ||
                exp instanceof PsiSuperExpression ||
                exp instanceof PsiClassObjectAccessExpression){
        } else if(exp instanceof PsiTypeCastExpression){
            calculateExceptionsThrownForTypeCast((PsiTypeCastExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiInstanceOfExpression){
            calculateExceptionsThrownForInstanceOf(
                    (PsiInstanceOfExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiReferenceExpression){
            final PsiReferenceExpression refExp = (PsiReferenceExpression) exp;
            final PsiExpression qualifier = refExp.getQualifierExpression();
            if(qualifier != null){
                calculateExceptionsThrown(qualifier, exceptionTypes);
            }
        } else if(exp instanceof PsiMethodCallExpression){
            calculateExceptionsThrownForMethodCall(
                    (PsiMethodCallExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiNewExpression){
            calculateExceptionsThrownForNewExpression((PsiNewExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiArrayInitializerExpression){
            calculateExceptionsThrownForArrayInitializerExpression(
                    (PsiArrayInitializerExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiArrayAccessExpression){
            calculateExceptionsThrownForArrayAccessExpression(
                    (PsiArrayAccessExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiPrefixExpression){
            calculateExceptionsThrownForPrefixException(
                    (PsiPrefixExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiPostfixExpression){
            calculateExceptionsThrownForPostixExpression(
                    (PsiPostfixExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiBinaryExpression){
            calculateExceptionsThrownForBinaryExpression(
                    (PsiBinaryExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiAssignmentExpression){
            calculateExceptionsThrownForAssignmentExpression(
                    (PsiAssignmentExpression) exp,
                    exceptionTypes);
        } else if(exp instanceof PsiConditionalExpression){
            calculateExceptionsThrownForConditionalExcpression(
                    (PsiConditionalExpression) exp,
                    exceptionTypes);
        }
    }

    private static void calculateExceptionsThrownForTypeCast(
            PsiTypeCastExpression typeCast,
            Set<PsiType> exceptionTypes){
        final PsiExpression operand = typeCast.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes);
    }

    private static void calculateExceptionsThrownForInstanceOf(
            PsiInstanceOfExpression instExp,
            Set<PsiType> exceptionTypes){
        final PsiExpression operand = instExp.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes);
    }

    private static void calculateExceptionsThrownForNewExpression(
            PsiNewExpression newExp,
            Set<PsiType> exceptionTypes){
        final PsiExpressionList argumentList = newExp.getArgumentList();
        if(argumentList != null){
            final PsiExpression[] args = argumentList.getExpressions();
            for(PsiExpression arg : args){
                calculateExceptionsThrown(arg, exceptionTypes);
            }
        }
        final PsiExpression[] arrayDims = newExp.getArrayDimensions();
        for(PsiExpression arrayDim : arrayDims){
            calculateExceptionsThrown(arrayDim, exceptionTypes);
        }
        final PsiExpression qualifier = newExp.getQualifier();
        calculateExceptionsThrown(qualifier, exceptionTypes);
        final PsiArrayInitializerExpression arrayInitializer =
                newExp.getArrayInitializer();
        calculateExceptionsThrown(arrayInitializer, exceptionTypes);
        final PsiMethod method = newExp.resolveMethod();
        if(method != null){
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] list =
                    throwsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement referenceElement : list){
                final PsiClass exceptionClass =
                        (PsiClass) referenceElement.resolve();
                if(exceptionClass != null){
                    final PsiManager psiManager = exceptionClass.getManager();
                    final PsiElementFactory factory = psiManager
                            .getElementFactory();
                    final PsiClassType exceptionType =
                            factory.createType(exceptionClass);
                    exceptionTypes.add(exceptionType);
                }
            }
        }
    }

    private static void calculateExceptionsThrownForMethodCall(
            PsiMethodCallExpression methExp,
            Set<PsiType> exceptionTypes){
        final PsiExpressionList argumentList = methExp.getArgumentList();
        final PsiExpression[] expressions = argumentList.getExpressions();
        for(PsiExpression expression : expressions){
            calculateExceptionsThrown(expression, exceptionTypes);
        }
        final PsiReferenceExpression methodExpression =
                methExp.getMethodExpression();
        calculateExceptionsThrown(methodExpression, exceptionTypes);
        final PsiMethod method = methExp.resolveMethod();

        if(method != null){
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] list =
                    throwsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement referenceElement : list){
                final PsiClass exceptionClass =
                        (PsiClass) referenceElement.resolve();
                if(exceptionClass != null){
                    final PsiManager psiManager = exceptionClass.getManager();
                    final PsiElementFactory factory = psiManager
                            .getElementFactory();
                    final PsiClassType exceptionType =
                            factory.createType(exceptionClass);
                    exceptionTypes.add(exceptionType);
                }
            }
        }
    }

    private static void calculateExceptionsThrownForConditionalExcpression(
            PsiConditionalExpression condExp,
            Set<PsiType> exceptionTypes){
        final PsiExpression condition = condExp.getCondition();
        final PsiExpression elseExpression = condExp.getElseExpression();
        final PsiExpression thenExpression = condExp.getThenExpression();
        calculateExceptionsThrown(condition, exceptionTypes);
        calculateExceptionsThrown(elseExpression, exceptionTypes);
        calculateExceptionsThrown(thenExpression, exceptionTypes);
    }

    private static void calculateExceptionsThrownForBinaryExpression(
            PsiBinaryExpression binaryExp,
            Set<PsiType> exceptionTypes){
        final PsiExpression lOperand = binaryExp.getLOperand();
        calculateExceptionsThrown(lOperand, exceptionTypes);
        final PsiExpression rhs = binaryExp.getROperand();
        calculateExceptionsThrown(rhs, exceptionTypes);
    }

    private static void calculateExceptionsThrownForAssignmentExpression(
            PsiAssignmentExpression assignment,
            Set<PsiType> exceptionTypes){
        final PsiExpression lOperand = assignment.getLExpression();
        calculateExceptionsThrown(lOperand, exceptionTypes);
        final PsiExpression rhs = assignment.getRExpression();
        calculateExceptionsThrown(rhs, exceptionTypes);
    }

    private static void calculateExceptionsThrownForArrayInitializerExpression(
            PsiArrayInitializerExpression arrayExp,
            Set<PsiType> exceptionTypes){
        final PsiExpression[] initializers = arrayExp.getInitializers();
        for(PsiExpression initializer : initializers){
            calculateExceptionsThrown(initializer, exceptionTypes);
        }
    }

    private static void calculateExceptionsThrownForArrayAccessExpression(
            PsiArrayAccessExpression arrayAccessExp,
            Set<PsiType> exceptionTypes){
        final PsiExpression arrayExpression =
                arrayAccessExp.getArrayExpression();
        calculateExceptionsThrown(arrayExpression, exceptionTypes);
        final PsiExpression indexExpression =
                arrayAccessExp.getIndexExpression();
        calculateExceptionsThrown(indexExpression, exceptionTypes);
    }

    private static void calculateExceptionsThrownForPrefixException(
            PsiPrefixExpression prefixExp,
            Set<PsiType> exceptionTypes){
        final PsiExpression operand = prefixExp.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes);
    }

    private static void calculateExceptionsThrownForPostixExpression(
            PsiPostfixExpression postfixExp,
            Set<PsiType> exceptionTypes){
        final PsiExpression operand = postfixExp.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes);
    }

    public static void calculateExceptionsThrownForCodeBlock(
            PsiCodeBlock block, Set<PsiType> exceptionTypes){
        if(block == null){
            return;
        }
        final PsiStatement[] statements = block.getStatements();
        for(PsiStatement statement : statements){
            calculateExceptionsThrownForStatement(statement, exceptionTypes);
        }
    }
}