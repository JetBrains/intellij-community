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
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ExceptionUtils {

  private ExceptionUtils() {}

  public static Set<PsiType> getExceptionTypesHandled(PsiTryStatement statement) {
    final Set<PsiType> out = new HashSet<PsiType>(10);
    final PsiParameter[] parameters = statement.getCatchBlockParameters();
    for (PsiParameter parameter : parameters) {
      final PsiType type = parameter.getType();
      if (type instanceof PsiDisjunctionType) {
        final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)type;
        final List<PsiType> disjunctions = disjunctionType.getDisjunctions();
        out.addAll(disjunctions);
      } else {
        out.add(type);
      }
    }
    return out;
  }

  private static void calculateExceptionsThrownForStatement(PsiStatement statement, Set<PsiType> exceptionTypes) {
    if (statement == null) {
      return;
    }
    if (statement instanceof PsiBreakStatement || statement instanceof PsiContinueStatement) {
      // don't do anything
    }
    else if (statement instanceof PsiReturnStatement) {
      final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        calculateExceptionsThrownForExpression(returnValue, exceptionTypes);
      }
    }
    else if (statement instanceof PsiThrowStatement) {
      final PsiThrowStatement throwStatement = (PsiThrowStatement)statement;
      calculateExceptionsThrownForThrowStatement(throwStatement, exceptionTypes);
    }
    else if (statement instanceof PsiExpressionListStatement) {
      final PsiExpressionListStatement expressionListStatement = (PsiExpressionListStatement)statement;
      calculateExceptionsThrownForExpressionListStatement(expressionListStatement, exceptionTypes);
    }
    else if (statement instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      final PsiExpression expression = expressionStatement.getExpression();
      calculateExceptionsThrownForExpression(expression, exceptionTypes);
    }
    else if (statement instanceof PsiAssertStatement) {
      final PsiAssertStatement assertStatement = (PsiAssertStatement)statement;
      calculateExceptionsThrownForAssertStatement(assertStatement, exceptionTypes);
    }
    else if (statement instanceof PsiDeclarationStatement) {
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      calculateExceptionsThrownForDeclarationStatement(declarationStatement, exceptionTypes);
    }
    else if (statement instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)statement;
      calculateExceptionsThrownForForStatement(forStatement, exceptionTypes);
    }
    else if (statement instanceof PsiForeachStatement) {
      final PsiForeachStatement foreachStatement = (PsiForeachStatement)statement;
      calculateExceptionsThrownForForeachStatement(foreachStatement, exceptionTypes);
    }
    else if (statement instanceof PsiWhileStatement) {
      final PsiWhileStatement whileStatement = (PsiWhileStatement)statement;
      calculateExceptionsThrownForWhileStatement(whileStatement, exceptionTypes);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      final PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)statement;
      calculateExceptionsThrownForDoWhileStatement(doWhileStatement, exceptionTypes);
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      final PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)statement;
      calculateExceptionsThrownForSynchronizedStatement(synchronizedStatement, exceptionTypes);
    }
    else if (statement instanceof PsiBlockStatement) {
      final PsiBlockStatement block = (PsiBlockStatement)statement;
      calculateExceptionsThrownForBlockStatement(block, exceptionTypes);
    }
    else if (statement instanceof PsiLabeledStatement) {
      final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)statement;
      calculateExceptionsThrownForLabeledStatement(labeledStatement, exceptionTypes);
    }
    else if (statement instanceof PsiIfStatement) {
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      calculateExceptionsThrownForIfStatement(ifStatement, exceptionTypes);
    }
    else if (statement instanceof PsiTryStatement) {
      final PsiTryStatement tryStatement = (PsiTryStatement)statement;
      calculateExceptionsThrownForTryStatement(tryStatement, exceptionTypes);
    }
    else if (statement instanceof PsiSwitchStatement) {
      final PsiSwitchStatement switchStatement = (PsiSwitchStatement)statement;
      calculateExceptionsThrownForSwitchStatement(switchStatement, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForLabeledStatement(PsiLabeledStatement labeledStatement, Set<PsiType> exceptionTypes) {
    final PsiStatement statement = labeledStatement.getStatement();
    calculateExceptionsThrownForStatement(statement, exceptionTypes);
  }

  private static void calculateExceptionsThrownForExpressionListStatement(PsiExpressionListStatement listStatement,
                                                                          Set<PsiType> exceptionTypes) {
    final PsiExpressionList expressionList = listStatement.getExpressionList();
    final PsiExpression[] expressions = expressionList.getExpressions();
    for (PsiExpression expression : expressions) {
      calculateExceptionsThrownForExpression(expression, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForDeclarationStatement(PsiDeclarationStatement declarationStatement,
                                                                       Set<PsiType> exceptionTypes) {
    final PsiElement[] elements = declarationStatement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)element;
        final PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          calculateExceptionsThrownForExpression(initializer, exceptionTypes);
        }
      }
    }
  }

  private static void calculateExceptionsThrownForAssertStatement(PsiAssertStatement assertStatement, Set<PsiType> exceptionTypes) {
    final PsiExpression assertCondition = assertStatement.getAssertCondition();
    calculateExceptionsThrownForExpression(assertCondition, exceptionTypes);
    final PsiExpression assertDescription = assertStatement.getAssertDescription();
    calculateExceptionsThrownForExpression(assertDescription, exceptionTypes);
  }

  private static void calculateExceptionsThrownForThrowStatement(PsiThrowStatement throwStatement, Set<PsiType> exceptionTypes) {
    final PsiExpression exception = throwStatement.getException();
    if (exception == null) {
      return;
    }
    final PsiType type = exception.getType();
    if (type != null) {
      exceptionTypes.add(type);
    }
    calculateExceptionsThrownForExpression(exception, exceptionTypes);
  }

  private static void calculateExceptionsThrownForSwitchStatement(PsiSwitchStatement switchStatement, Set<PsiType> exceptionTypes) {
    final PsiExpression switchExpression = switchStatement.getExpression();
    calculateExceptionsThrownForExpression(switchExpression, exceptionTypes);
    final PsiCodeBlock body = switchStatement.getBody();
    calculateExceptionsThrownForCodeBlock(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForTryStatement(PsiTryStatement tryStatement, Set<PsiType> exceptionTypes) {
    final Set<PsiType> exceptionThrown = new HashSet<PsiType>(10);
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      calculateExceptionsThrownForResourceList(resourceList, exceptionTypes);
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    calculateExceptionsThrownForCodeBlock(tryBlock, exceptionThrown);
    final Set<PsiType> exceptionHandled = getExceptionTypesHandled(tryStatement);
    for (PsiType thrownType : exceptionThrown) {
      boolean found = false;
      for (PsiType handledType : exceptionHandled) {
        if (handledType.isAssignableFrom(thrownType)) {
          found = true;
          break;
        }
      }
      if (!found) {
        exceptionTypes.add(thrownType);
      }
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      calculateExceptionsThrownForCodeBlock(finallyBlock, exceptionTypes);
    }
    final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (PsiCodeBlock catchBlock : catchBlocks) {
      calculateExceptionsThrownForCodeBlock(catchBlock, exceptionTypes);
    }
  }

  public static void calculateExceptionsThrownForResourceList(PsiResourceList resourceList, Set<PsiType> exceptionTypes) {
    final List<PsiResourceVariable> resourceVariables = resourceList.getResourceVariables();
    for (PsiResourceVariable variable : resourceVariables) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        calculateExceptionsThrownForExpression(initializer, exceptionTypes);
      }
      final PsiType type = variable.getType();
      final PsiClassType autoCloseable = getJavaLangAutoCloseable(resourceList);
      if (!(type instanceof PsiClassType) || !autoCloseable.isAssignableFrom(type)) {
        continue;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        continue;
      }
      final PsiMethod[] closeMethods = aClass.findMethodsByName("close", true);
      for (PsiMethod method : closeMethods) {
        final PsiParameterList list = method.getParameterList();
        if (list.getParametersCount() == 0) {
          calculateExceptionsDeclaredForMethod(method, exceptionTypes);
          break;
        }
      }
    }
  }

  private static PsiClassType getJavaLangAutoCloseable(PsiElement context) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    return factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, context.getResolveScope());
  }

  private static void calculateExceptionsThrownForIfStatement(PsiIfStatement ifStatement, Set<PsiType> exceptionTypes) {
    final PsiExpression condition = ifStatement.getCondition();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    calculateExceptionsThrownForStatement(thenBranch, exceptionTypes);
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    calculateExceptionsThrownForStatement(elseBranch, exceptionTypes);
  }

  private static void calculateExceptionsThrownForBlockStatement(PsiBlockStatement block, Set<PsiType> exceptionTypes) {
    final PsiCodeBlock codeBlock = block.getCodeBlock();
    calculateExceptionsThrownForCodeBlock(codeBlock, exceptionTypes);
  }

  private static void calculateExceptionsThrownForSynchronizedStatement(PsiSynchronizedStatement synchronizedStatement,
                                                                        Set<PsiType> exceptionTypes) {
    final PsiExpression lockExpression = synchronizedStatement.getLockExpression();
    if (lockExpression != null) {
      calculateExceptionsThrownForExpression(lockExpression, exceptionTypes);
    }
    final PsiCodeBlock body = synchronizedStatement.getBody();
    calculateExceptionsThrownForCodeBlock(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForDoWhileStatement(PsiDoWhileStatement doWhileStatement, Set<PsiType> exceptionTypes) {
    final PsiExpression condition = doWhileStatement.getCondition();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    final PsiStatement body = doWhileStatement.getBody();
    calculateExceptionsThrownForStatement(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForWhileStatement(PsiWhileStatement whileStatement, Set<PsiType> exceptionTypes) {
    final PsiExpression condition = whileStatement.getCondition();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    final PsiStatement body = whileStatement.getBody();
    calculateExceptionsThrownForStatement(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForForStatement(PsiForStatement forStatement, Set<PsiType> exceptionTypes) {
    final PsiStatement initialization = forStatement.getInitialization();
    calculateExceptionsThrownForStatement(initialization, exceptionTypes);
    final PsiExpression condition = forStatement.getCondition();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    final PsiStatement update = forStatement.getUpdate();
    calculateExceptionsThrownForStatement(update, exceptionTypes);
    final PsiStatement body = forStatement.getBody();
    calculateExceptionsThrownForStatement(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForForeachStatement(PsiForeachStatement foreachStatement, Set<PsiType> exceptionTypes) {
    final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
    calculateExceptionsThrownForExpression(iteratedValue, exceptionTypes);
    final PsiStatement body = foreachStatement.getBody();
    calculateExceptionsThrownForStatement(body, exceptionTypes);
  }

  private static void calculateExceptionsThrownForExpression(PsiExpression expression, Set<PsiType> exceptionTypes) {
    if (expression == null) {
      return;
    }
    if (expression instanceof PsiThisExpression || expression instanceof PsiLiteralExpression ||
        expression instanceof PsiSuperExpression || expression instanceof PsiClassObjectAccessExpression) {
    }
    else if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
      calculateExceptionsThrownForTypeCast(typeCastExpression, exceptionTypes);
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
      calculateExceptionsThrownForInstanceOf(instanceOfExpression, exceptionTypes);
    }
    else if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier != null) {
        calculateExceptionsThrownForExpression(qualifier, exceptionTypes);
      }
    }
    else if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      calculateExceptionsThrownForMethodCall(methodCallExpression, exceptionTypes);
    }
    else if (expression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      calculateExceptionsThrownForNewExpression(newExpression, exceptionTypes);
    }
    else if (expression instanceof PsiArrayInitializerExpression) {
      final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)expression;
      calculateExceptionsThrownForArrayInitializerExpression(arrayInitializerExpression, exceptionTypes);
    }
    else if (expression instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)expression;
      calculateExceptionsThrownForArrayAccessExpression(arrayAccessExpression, exceptionTypes);
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      calculateExceptionsThrownForPrefixException(prefixExpression, exceptionTypes);
    }
    else if (expression instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)expression;
      calculateExceptionsThrownForPostfixExpression(postfixExpression, exceptionTypes);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      calculateExceptionsThrownForPolyadicExpression(polyadicExpression, exceptionTypes);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      calculateExceptionsThrownForAssignmentExpression(assignmentExpression, exceptionTypes);
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      calculateExceptionsThrownForConditionalExpression(conditionalExpression, exceptionTypes);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiExpression innerExpression = parenthesizedExpression.getExpression();
      calculateExceptionsThrownForExpression(innerExpression, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForTypeCast(PsiTypeCastExpression typeCastExpression, Set<PsiType> exceptionTypes) {
    final PsiExpression operand = typeCastExpression.getOperand();
    calculateExceptionsThrownForExpression(operand, exceptionTypes);
  }

  private static void calculateExceptionsThrownForInstanceOf(PsiInstanceOfExpression instanceOfExpression, Set<PsiType> exceptionTypes) {
    final PsiExpression operand = instanceOfExpression.getOperand();
    calculateExceptionsThrownForExpression(operand, exceptionTypes);
  }

  private static void calculateExceptionsThrownForNewExpression(PsiNewExpression newExpression, Set<PsiType> exceptionTypes) {
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList != null) {
      final PsiExpression[] arguments = argumentList.getExpressions();
      for (PsiExpression argument : arguments) {
        calculateExceptionsThrownForExpression(argument, exceptionTypes);
      }
    }
    final PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
    for (PsiExpression arrayDimension : arrayDimensions) {
      calculateExceptionsThrownForExpression(arrayDimension, exceptionTypes);
    }
    final PsiExpression qualifier = newExpression.getQualifier();
    calculateExceptionsThrownForExpression(qualifier, exceptionTypes);
    final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
    calculateExceptionsThrownForExpression(arrayInitializer, exceptionTypes);
    final PsiMethod method = newExpression.resolveMethod();
    calculateExceptionsDeclaredForMethod(method, exceptionTypes);
  }

  private static void calculateExceptionsThrownForMethodCall(PsiMethodCallExpression methodCallExpression, Set<PsiType> exceptionTypes) {
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] expressions = argumentList.getExpressions();
    for (PsiExpression expression : expressions) {
      calculateExceptionsThrownForExpression(expression, exceptionTypes);
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    calculateExceptionsThrownForExpression(methodExpression, exceptionTypes);
    final PsiMethod method = methodCallExpression.resolveMethod();
    calculateExceptionsDeclaredForMethod(method, exceptionTypes);
  }

  public static void calculateExceptionsDeclaredForMethod(PsiMethod method, Set<PsiType> exceptionTypes) {
    if (method == null) {
      return;
    }
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiClassType[] types = throwsList.getReferencedTypes();
    Collections.addAll(exceptionTypes, types);
  }

  private static void calculateExceptionsThrownForConditionalExpression(PsiConditionalExpression conditionalExpression,
                                                                        Set<PsiType> exceptionTypes) {
    final PsiExpression condition = conditionalExpression.getCondition();
    final PsiExpression elseExpression = conditionalExpression.getElseExpression();
    final PsiExpression thenExpression = conditionalExpression.getThenExpression();
    calculateExceptionsThrownForExpression(condition, exceptionTypes);
    calculateExceptionsThrownForExpression(elseExpression, exceptionTypes);
    calculateExceptionsThrownForExpression(thenExpression, exceptionTypes);
  }

  private static void calculateExceptionsThrownForPolyadicExpression(PsiPolyadicExpression polyadicExpression, Set<PsiType> exceptionTypes) {
    final PsiExpression[] operands = polyadicExpression.getOperands();
    for (PsiExpression operand : operands) {
      calculateExceptionsThrownForExpression(operand, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForAssignmentExpression(PsiAssignmentExpression assignmentExpression,
                                                                       Set<PsiType> exceptionTypes) {
    final PsiExpression lOperand = assignmentExpression.getLExpression();
    calculateExceptionsThrownForExpression(lOperand, exceptionTypes);
    final PsiExpression rhs = assignmentExpression.getRExpression();
    calculateExceptionsThrownForExpression(rhs, exceptionTypes);
  }

  private static void calculateExceptionsThrownForArrayInitializerExpression(PsiArrayInitializerExpression arrayInitializerExpression,
                                                                             Set<PsiType> exceptionTypes) {
    final PsiExpression[] initializers = arrayInitializerExpression.getInitializers();
    for (PsiExpression initializer : initializers) {
      calculateExceptionsThrownForExpression(initializer, exceptionTypes);
    }
  }

  private static void calculateExceptionsThrownForArrayAccessExpression(PsiArrayAccessExpression arrayAccessExpression,
                                                                        Set<PsiType> exceptionTypes) {
    final PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
    calculateExceptionsThrownForExpression(arrayExpression, exceptionTypes);
    final PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
    calculateExceptionsThrownForExpression(indexExpression, exceptionTypes);
  }

  private static void calculateExceptionsThrownForPrefixException(PsiPrefixExpression prefixExpression, Set<PsiType> exceptionTypes) {
    final PsiExpression operand = prefixExpression.getOperand();
    calculateExceptionsThrownForExpression(operand, exceptionTypes);
  }

  private static void calculateExceptionsThrownForPostfixExpression(PsiPostfixExpression postfixExpression, Set<PsiType> exceptionTypes) {
    final PsiExpression operand = postfixExpression.getOperand();
    calculateExceptionsThrownForExpression(operand, exceptionTypes);
  }

  public static void calculateExceptionsThrownForCodeBlock(
    PsiCodeBlock codeBlock, Set<PsiType> exceptionTypes) {
    if (codeBlock == null) {
      return;
    }
    final PsiStatement[] statements = codeBlock.getStatements();
    for (PsiStatement statement : statements) {
      calculateExceptionsThrownForStatement(statement, exceptionTypes);
    }
  }
}
