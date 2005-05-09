package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class ExceptionUtils{
    private ExceptionUtils(){
        super();
    }

    public static Set getExceptionTypesHandled(PsiTryStatement statement){
        final Set out = new HashSet(10);
        final PsiParameter[] params = statement.getCatchBlockParameters();
        for(PsiParameter param : params){
            final PsiType type = param.getType();
            out.add(type);
        }
        return out;
    }

    private static void calculateExceptionsThrownForStatement(PsiStatement statement,
                                                              Set exceptionTypes,
                                                              PsiElementFactory factory){
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
                calculateExceptionsThrown(returnValue, exceptionTypes, factory);
            }
        } else if(statement instanceof PsiThrowStatement){
            calculateExceptionsThrownForThrowStatement((PsiThrowStatement) statement,
                                                       exceptionTypes, factory);
        } else if(statement instanceof PsiExpressionListStatement){
            final PsiExpressionListStatement listStatement =
                    (PsiExpressionListStatement) statement;
            calculateExceptionsThrownForExpressionListStatement(listStatement,
                                                                exceptionTypes,
                                                                factory);
        } else if(statement instanceof PsiExpressionStatement){
            final PsiExpressionStatement expStatement =
                    (PsiExpressionStatement) statement;
            final PsiExpression expression = expStatement.getExpression();
            calculateExceptionsThrown(expression, exceptionTypes, factory);
        } else if(statement instanceof PsiAssertStatement){
            final PsiAssertStatement assertStatement =
                    (PsiAssertStatement) statement;
            calculateExceptionsThrownForAssertStatement(assertStatement,
                                                        exceptionTypes,
                                                        factory);
        } else if(statement instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement declStatement =
                    (PsiDeclarationStatement) statement;
            calculateExceptionsThrownForDeclarationStatemt(declStatement,
                                                           exceptionTypes,
                                                           factory);
        } else if(statement instanceof PsiForStatement){
            calculateExceptionsThrownForForExpression((PsiForStatement) statement,
                                                      exceptionTypes, factory);
        } else if(statement instanceof PsiWhileStatement){
            calculateExceptionsThrownForWhileStatement((PsiWhileStatement) statement,
                                                       exceptionTypes, factory);
        } else if(statement instanceof PsiDoWhileStatement){
            calculateExceptionsThrownForDoWhileStatement((PsiDoWhileStatement) statement,
                                                         exceptionTypes,
                                                         factory);
        } else if(statement instanceof PsiSynchronizedStatement){
            calculateExceptionsThrownForSynchronizedStatement((PsiSynchronizedStatement) statement,
                                                              exceptionTypes,
                                                              factory);
        } else if(statement instanceof PsiBlockStatement){
            final PsiBlockStatement block = (PsiBlockStatement) statement;
            calculateExceptionsThrownForBlockStatement(block, exceptionTypes,
                                                       factory);
        } else if(statement instanceof PsiLabeledStatement){
            final PsiLabeledStatement labeledStatement =
                    (PsiLabeledStatement) statement;
            calculateExceptionsThrownForLabeledStatement(labeledStatement,
                                                         exceptionTypes,
                                                         factory);
        } else if(statement instanceof PsiIfStatement){
            final PsiIfStatement ifStatement = (PsiIfStatement) statement;
            calculateExceptionsThrownForIfStatement(ifStatement, exceptionTypes,
                                                    factory);
        } else if(statement instanceof PsiTryStatement){
            final PsiTryStatement tryStatement = (PsiTryStatement) statement;
            calculateExceptionsThrownForTryStatement(tryStatement, factory,
                                                     exceptionTypes);
        } else if(statement instanceof PsiSwitchStatement){
            final PsiSwitchStatement switchStatement =
                    (PsiSwitchStatement) statement;
            calculateExceptionsThrownForSwitchStatement(switchStatement,
                                                        exceptionTypes,
                                                        factory);
        }
    }

    private static void calculateExceptionsThrownForLabeledStatement(PsiLabeledStatement labeledStatement,
                                                                     Set exceptionTypes,
                                                                     PsiElementFactory factory){
        final PsiStatement statement = labeledStatement.getStatement();
        calculateExceptionsThrownForStatement(statement, exceptionTypes,
                                              factory);
    }

    private static void calculateExceptionsThrownForExpressionListStatement(PsiExpressionListStatement listStatement,
                                                                            Set exceptionTypes,
                                                                            PsiElementFactory factory){
        final PsiExpressionList expressionList =
                listStatement.getExpressionList();
        final PsiExpression[] expressions = expressionList.getExpressions();
        for(PsiExpression expression : expressions){
            calculateExceptionsThrown(expression, exceptionTypes, factory);
        }
    }

    private static void calculateExceptionsThrownForDeclarationStatemt(PsiDeclarationStatement declStatement,
                                                                       Set exceptionTypes,
                                                                       PsiElementFactory factory){
        final PsiElement[] elements = declStatement.getDeclaredElements();
        for(PsiElement element : elements){
            final PsiVariable var = (PsiVariable) element;
            final PsiExpression initializer = var.getInitializer();
            if(initializer != null){
                calculateExceptionsThrown(initializer, exceptionTypes, factory);
            }
        }
    }

    private static void calculateExceptionsThrownForAssertStatement(PsiAssertStatement assertStatement,
                                                                    Set exceptionTypes,
                                                                    PsiElementFactory factory){
        final PsiExpression assertCondition =
                assertStatement.getAssertCondition();
        calculateExceptionsThrown(assertCondition, exceptionTypes, factory);
        final PsiExpression assertDescription =
                assertStatement.getAssertDescription();
        calculateExceptionsThrown(assertDescription, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForThrowStatement(PsiThrowStatement throwStatement,
                                                                   Set exceptionTypes,
                                                                   PsiElementFactory factory){
        final PsiExpression exception = throwStatement.getException();
        final PsiType type = exception.getType();
        exceptionTypes.add(type);
        calculateExceptionsThrown(exception, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForSwitchStatement(PsiSwitchStatement switchStatement,
                                                                    Set exceptionTypes,
                                                                    PsiElementFactory factory){
        final PsiExpression switchExpression = switchStatement.getExpression();
        calculateExceptionsThrown(switchExpression, exceptionTypes, factory);
        final PsiCodeBlock body = switchStatement.getBody();
        calculateExceptionsThrownForCodeBlock(body, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForTryStatement(PsiTryStatement tryStatement,
                                                                 PsiElementFactory factory,
                                                                 Set exceptionTypes){
        final Set exceptionThrown = new HashSet(10);
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        calculateExceptionsThrownForCodeBlock(tryBlock, exceptionThrown,
                                              factory);
        final Set exceptionHandled =
                ExceptionUtils.getExceptionTypesHandled(tryStatement);
        for(Object aExceptionThrown : exceptionThrown){
            final PsiType thrownType = (PsiType) aExceptionThrown;
            boolean found = false;
            for(Object aExceptionHandled : exceptionHandled){
                final PsiType handledType = (PsiType) aExceptionHandled;
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
            calculateExceptionsThrownForCodeBlock(finallyBlock, exceptionTypes,
                                                  factory);
        }

        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for(PsiCodeBlock catchBlock : catchBlocks){
            calculateExceptionsThrownForCodeBlock(catchBlock,
                                                  exceptionTypes, factory);
        }
    }

    private static void calculateExceptionsThrownForIfStatement(PsiIfStatement ifStatement,
                                                                Set exceptionTypes,
                                                                PsiElementFactory factory){
        final PsiExpression condition = ifStatement.getCondition();
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        calculateExceptionsThrown(condition, exceptionTypes, factory);
        calculateExceptionsThrownForStatement(thenBranch, exceptionTypes,
                                              factory);
        calculateExceptionsThrownForStatement(elseBranch, exceptionTypes,
                                              factory);
    }

    private static void calculateExceptionsThrownForBlockStatement(PsiBlockStatement block,
                                                                   Set exceptionTypes,
                                                                   PsiElementFactory factory){
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        calculateExceptionsThrownForCodeBlock(codeBlock, exceptionTypes,
                                              factory);
    }

    private static void calculateExceptionsThrownForSynchronizedStatement(PsiSynchronizedStatement syncStatement,
                                                                          Set exceptionTypes,
                                                                          PsiElementFactory factory){
        final PsiExpression lockExpression = syncStatement.getLockExpression();
        if(lockExpression != null){
            calculateExceptionsThrown(lockExpression, exceptionTypes, factory);
        }
        final PsiCodeBlock body = syncStatement.getBody();
        calculateExceptionsThrownForCodeBlock(body, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForDoWhileStatement(PsiDoWhileStatement loopStatement,
                                                                     Set exceptionTypes,
                                                                     PsiElementFactory factory){
        final PsiExpression condition = loopStatement.getCondition();
        calculateExceptionsThrown(condition, exceptionTypes, factory);
        final PsiStatement body = loopStatement.getBody();
        calculateExceptionsThrownForStatement(body, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForWhileStatement(PsiWhileStatement loopStatement,
                                                                   Set exceptionTypes,
                                                                   PsiElementFactory factory){
        final PsiExpression condition = loopStatement.getCondition();
        calculateExceptionsThrown(condition, exceptionTypes, factory);
        final PsiStatement body = loopStatement.getBody();
        calculateExceptionsThrownForStatement(body, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForForExpression(PsiForStatement loopStatement,
                                                                  Set exceptionTypes,
                                                                  PsiElementFactory factory){
        final PsiStatement initialization = loopStatement.getInitialization();
        final PsiExpression condition = loopStatement.getCondition();
        final PsiStatement update = loopStatement.getUpdate();
        final PsiStatement body = loopStatement.getBody();
        calculateExceptionsThrownForStatement(initialization, exceptionTypes,
                                              factory);
        calculateExceptionsThrown(condition, exceptionTypes, factory);
        calculateExceptionsThrownForStatement(update, exceptionTypes, factory);
        calculateExceptionsThrownForStatement(body, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrown(PsiExpression exp,
                                                  Set exceptionTypes,
                                                  PsiElementFactory factory){
        if(exp == null){
            return;
        }
        if(exp instanceof PsiThisExpression ||
                                exp instanceof PsiLiteralExpression ||
                                exp instanceof PsiSuperExpression ||
                                exp instanceof PsiClassObjectAccessExpression){
        } else if(exp instanceof PsiTypeCastExpression){
            calculateExceptionsThrownForTypeCast((PsiTypeCastExpression) exp,
                                                 exceptionTypes, factory);
        } else if(exp instanceof PsiInstanceOfExpression){
            calculateExceptionsThrownForInstanceOf((PsiInstanceOfExpression) exp,
                                                   exceptionTypes, factory);
        } else if(exp instanceof PsiReferenceExpression){
            final PsiReferenceExpression refExp = (PsiReferenceExpression) exp;
            final PsiExpression qualifier = refExp.getQualifierExpression();
            if(qualifier != null){
                calculateExceptionsThrown(qualifier, exceptionTypes, factory);
            }
        } else if(exp instanceof PsiMethodCallExpression){
            calculateExceptionsThrownForMethodCall((PsiMethodCallExpression) exp,
                                                   exceptionTypes, factory);
        } else if(exp instanceof PsiNewExpression){
            calculateExceptionsThrownForNewExpression((PsiNewExpression) exp,
                                                      exceptionTypes, factory);
        } else if(exp instanceof PsiArrayInitializerExpression){
            calculateExceptionsThrownForArrayInitializerExpression((PsiArrayInitializerExpression) exp,
                                                                   exceptionTypes,
                                                                   factory);
        } else if(exp instanceof PsiArrayAccessExpression){
            calculateExceptionsThrownForArrayAccessExpression((PsiArrayAccessExpression) exp,
                                                              exceptionTypes,
                                                              factory);
        } else if(exp instanceof PsiPrefixExpression){
            calculateExceptionsThrownForPrefixException((PsiPrefixExpression) exp,
                                                        exceptionTypes,
                                                        factory);
        } else if(exp instanceof PsiPostfixExpression){
            calculateExceptionsThrownForPostixExpression((PsiPostfixExpression) exp,
                                                         exceptionTypes,
                                                         factory);
        } else if(exp instanceof PsiBinaryExpression){
            calculateExceptionsThrownForBinaryExpression((PsiBinaryExpression) exp,
                                                         exceptionTypes,
                                                         factory);
        } else if(exp instanceof PsiConditionalExpression){
            calculateExceptionsThrownForConditionalExcpression((PsiConditionalExpression) exp,
                                                               exceptionTypes,
                                                               factory);
        }
    }

    private static void calculateExceptionsThrownForTypeCast(PsiTypeCastExpression typeCast,
                                                             Set exceptionTypes,
                                                             PsiElementFactory factory){
        final PsiExpression operand = typeCast.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForInstanceOf(PsiInstanceOfExpression instExp,
                                                               Set exceptionTypes,
                                                               PsiElementFactory factory){
        final PsiExpression operand = instExp.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForNewExpression(PsiNewExpression newExp,
                                                                  Set exceptionTypes,
                                                                  PsiElementFactory factory){
        final PsiExpressionList argumentList = newExp.getArgumentList();
        if(argumentList != null){
            final PsiExpression[] args = argumentList.getExpressions();
            for(PsiExpression arg : args){
                calculateExceptionsThrown(arg, exceptionTypes, factory);
            }
        }
        final PsiExpression[] arrayDims = newExp.getArrayDimensions();
        for(PsiExpression arrayDim : arrayDims){
            calculateExceptionsThrown(arrayDim, exceptionTypes, factory);
        }
        final PsiExpression qualifier = newExp.getQualifier();
        calculateExceptionsThrown(qualifier, exceptionTypes, factory);
        final PsiArrayInitializerExpression arrayInitializer =
                newExp.getArrayInitializer();
        calculateExceptionsThrown(arrayInitializer, exceptionTypes, factory);
        final PsiMethod method = newExp.resolveMethod();
        if(method != null){
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] list =
                    throwsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement referenceElement : list){
                final PsiClass exceptionClass =
                        (PsiClass) referenceElement.resolve();
                final PsiClassType exceptionType =
                        factory.createType(exceptionClass);
                exceptionTypes.add(exceptionType);
            }
        }
    }

    private static void calculateExceptionsThrownForMethodCall(PsiMethodCallExpression methExp,
                                                               Set exceptionTypes,
                                                               PsiElementFactory factory){
        final PsiExpressionList argumentList = methExp.getArgumentList();
        if(argumentList != null){
            final PsiExpression[] expressions = argumentList.getExpressions();
            for(PsiExpression expression : expressions){
                calculateExceptionsThrown(expression, exceptionTypes,
                                          factory);
            }
        }
        final PsiReferenceExpression methodExpression =
                methExp.getMethodExpression();
        calculateExceptionsThrown(methodExpression, exceptionTypes, factory);
        final PsiMethod method = methExp.resolveMethod();

        if(method != null){
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] list =
                    throwsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement referenceElement : list){
                final PsiClass exceptionClass =
                        (PsiClass) referenceElement.resolve();
                final PsiClassType exceptionType =
                        factory.createType(exceptionClass);
                exceptionTypes.add(exceptionType);
            }
        }
    }

    private static void calculateExceptionsThrownForConditionalExcpression(PsiConditionalExpression condExp,
                                                                           Set exceptionTypes,
                                                                           PsiElementFactory factory){
        final PsiExpression condition = condExp.getCondition();
        final PsiExpression elseExpression = condExp.getElseExpression();
        final PsiExpression thenExpression = condExp.getThenExpression();
        calculateExceptionsThrown(condition, exceptionTypes, factory);
        calculateExceptionsThrown(elseExpression, exceptionTypes, factory);
        calculateExceptionsThrown(thenExpression, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForBinaryExpression(PsiBinaryExpression binaryExp,
                                                                     Set exceptionTypes,
                                                                     PsiElementFactory factory){
        final PsiExpression lOperand = binaryExp.getLOperand();
        calculateExceptionsThrown(lOperand, exceptionTypes, factory);
        final PsiExpression rhs = binaryExp.getROperand();
        calculateExceptionsThrown(rhs, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForArrayInitializerExpression(PsiArrayInitializerExpression arrayExp,
                                                                               Set exceptionTypes,
                                                                               PsiElementFactory factory){
        final PsiExpression[] initializers = arrayExp.getInitializers();
        for(PsiExpression initializer : initializers){
            calculateExceptionsThrown(initializer, exceptionTypes, factory);
        }
    }

    private static void calculateExceptionsThrownForArrayAccessExpression(PsiArrayAccessExpression arrayAccessExp,
                                                                          Set exceptionTypes,
                                                                          PsiElementFactory factory){
        final PsiExpression arrayExpression =
                arrayAccessExp.getArrayExpression();
        calculateExceptionsThrown(arrayExpression, exceptionTypes, factory);
        final PsiExpression indexExpression =
                arrayAccessExp.getIndexExpression();
        calculateExceptionsThrown(indexExpression, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForPrefixException(PsiPrefixExpression prefixExp,
                                                                    Set exceptionTypes,
                                                                    PsiElementFactory factory){
        final PsiExpression operand = prefixExp.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes, factory);
    }

    private static void calculateExceptionsThrownForPostixExpression(PsiPostfixExpression postfixExp,
                                                                     Set exceptionTypes,
                                                                     PsiElementFactory factory){
        final PsiExpression operand = postfixExp.getOperand();
        calculateExceptionsThrown(operand, exceptionTypes, factory);
    }

    public static void calculateExceptionsThrownForCodeBlock(PsiCodeBlock block,
                                                             Set exceptionTypes,
                                                             PsiElementFactory factory){
        if(block == null){
            return;
        }
        final PsiStatement[] statements = block.getStatements();
        for(PsiStatement statement : statements){
            calculateExceptionsThrownForStatement(statement, exceptionTypes,
                                                  factory);
        }
    }
}
