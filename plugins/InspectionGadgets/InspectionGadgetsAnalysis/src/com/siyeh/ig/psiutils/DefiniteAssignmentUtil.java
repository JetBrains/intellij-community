/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of Java Language Specification, Chapter 16. Definite Assignment
 * with some changes to match javac behavior where it does not match the specification.
 * @author Bas Leijdekkers
 */
public final class DefiniteAssignmentUtil {

  public static void checkVariable(PsiVariable variable, DefiniteAssignment definiteAssignment) {
    if (variable.getInitializer() != null) {
      throw new IllegalArgumentException("variable has initializer, check for assignment to the field");
    }
    if (variable instanceof PsiField) {
      final PsiField field = (PsiField)variable;
      checkField(field, definiteAssignment);
    }
    else if (variable instanceof PsiParameter) {
      throw new IllegalArgumentException("parameter has implicit initializer, check for assignment to the parameter");
    }
    else if (variable instanceof PsiLocalVariable) {
      final PsiLocalVariable localVariable = (PsiLocalVariable)variable;
      final PsiElement parent = localVariable.getParent();
      assert parent instanceof PsiDeclarationStatement;
      PsiStatement statement = (PsiStatement)parent;
      while (statement != null) {
        checkStatement(statement, definiteAssignment);
        statement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      }
    }
    else {
      assert false;
    }
  }

  private static void checkField(PsiField field, DefiniteAssignment definiteAssignment) {
    if (field.getInitializer() != null) {
      definiteAssignment.set(true, false);
    }
    final PsiClass aClass = field.getContainingClass();
    if (aClass == null) {
      return;
    }
    final PsiElement[] children = aClass.getChildren();
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    for (PsiElement child : children) {
      if (child instanceof PsiField) {
        final PsiField otherField = (PsiField)child;
        if (otherField.hasModifierProperty(PsiModifier.STATIC) == isStatic) {
          checkExpression(otherField.getInitializer(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
        }
      }
      else if (child instanceof PsiClassInitializer) {
        final PsiClassInitializer classInitializer = (PsiClassInitializer)child;
        if (classInitializer.hasModifierProperty(PsiModifier.STATIC) == isStatic) {
          checkCodeBlock(classInitializer.getBody(), definiteAssignment);
        }
      }
    }
    if (!isStatic) {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length != 0) { // missing from spec?
        final boolean da = definiteAssignment.isDefinitelyAssigned();
        final boolean du = definiteAssignment.isDefinitelyUnassigned();
        boolean resultDa = true;
        boolean resultDu = true;
        for (PsiMethod constructor : constructors) {
          checkConstructor(constructor, definiteAssignment);
          resultDa &= definiteAssignment.isDefinitelyAssigned();
          resultDu &= definiteAssignment.isDefinitelyUnassigned();
          if (definiteAssignment.stop()) return;
          definiteAssignment.set(da, du);
        }
        definiteAssignment.set(resultDa, resultDu);
      }
    }
  }

  private static boolean isAlternateConstructorInvocation(PsiStatement statement) {
    return isConstructorInvocation(statement, PsiKeyword.THIS);
  }

  private static boolean isSuperClassConstructorInvocation(PsiStatement statement) {
    return isConstructorInvocation(statement, PsiKeyword.SUPER);
  }

  private static boolean isConstructorInvocation(PsiStatement statement, @NotNull String keyword) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
    final PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    return keyword.equals(methodExpression.getReferenceName());
  }

  private static void checkAnonymousClass(PsiAnonymousClass anonymousClass, DefiniteAssignment definiteAssignment) {
    for (PsiField field : anonymousClass.getFields()) {
      checkExpression(field.getInitializer(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    }
    final PsiClassInitializer[] initializers = anonymousClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      checkCodeBlock(initializer.getBody(), definiteAssignment);
    }
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    final boolean du = definiteAssignment.isDefinitelyUnassigned();
    for (PsiMethod method : anonymousClass.getMethods()) {
      definiteAssignment.set(true, false);
      checkCodeBlock(method.getBody(), definiteAssignment);
    }
    definiteAssignment.set(da, du);
  }

  private static void checkClass(PsiClass aClass, DefiniteAssignment definiteAssignment) {
    for (PsiField field : aClass.getFields()) {
      checkExpression(field.getInitializer(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    }
    for (PsiClassInitializer initializer : aClass.getInitializers()) {
      checkCodeBlock(initializer.getBody(), definiteAssignment);
    }
    for (PsiMethod method : aClass.getMethods()) {
      checkCodeBlock(method.getBody(), definiteAssignment);
    }
    for (PsiClass innerClass : aClass.getInnerClasses()) {
      checkClass(innerClass, definiteAssignment);
    }
  }

  private static void checkConstructor(PsiMethod constructor, DefiniteAssignment definiteAssignment) {
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    final boolean du = definiteAssignment.isDefinitelyUnassigned();
    definiteAssignment.set(false, true);
    final PsiCodeBlock body = constructor.getBody();
    if (body == null) {
      return;
    }
    final PsiStatement[] statements = body.getStatements();
    boolean superCalled = false;
    for (int i = 0; i < statements.length; i++) {
      final PsiStatement statement = statements[i];
      if (i == 0) {
        if (isAlternateConstructorInvocation(statement)) {
          checkStatement(statement, definiteAssignment);
          definiteAssignment.set(true, false);
          superCalled = true;
          continue;
        }
        else if (isSuperClassConstructorInvocation(statement)) {
          checkStatement(statement, definiteAssignment);
          definiteAssignment.set(da, du);
          superCalled = true;
          continue;
        }
      }
      if (!superCalled) {
        // implicit super call
        definiteAssignment.set(da, du);
        superCalled = true;
      }
      checkStatement(statement,definiteAssignment);
    }
    if (!superCalled) {
      // constructor has no statements
      definiteAssignment.set(da, du);
    }
    definiteAssignment.andDefiniteAssignmentBeforeReturn(constructor); // missing from spec?
  }

  private static void checkCodeBlock(@Nullable PsiCodeBlock codeBlock, DefiniteAssignment definiteAssignment) {
    if (codeBlock == null) {
      return;
    }
    for (PsiStatement statement : codeBlock.getStatements()) {
      checkStatement(statement, definiteAssignment);
    }
  }

  private static void checkLocalVariable(PsiLocalVariable localVariable, DefiniteAssignment definiteAssignment) {
    final PsiExpression initializer = localVariable.getInitializer();
    if (initializer != null && definiteAssignment.getVariable() == localVariable) {
      definiteAssignment.set(true, false);
    }
    checkExpression(initializer, definiteAssignment, BooleanExpressionValue.UNDEFINED);
  }

  private static void checkStatement(@Nullable PsiStatement statement, DefiniteAssignment definiteAssignment) {
    if (statement == null || definiteAssignment.stop() || statement instanceof PsiEmptyStatement) {
      return;
    }
    if (statement instanceof PsiAssertStatement) {
      final PsiAssertStatement assertStatement = (PsiAssertStatement)statement;
      checkAssertStatement(assertStatement, definiteAssignment);
    }
    else if (statement instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
      checkCodeBlock(blockStatement.getCodeBlock(), definiteAssignment);
    }
    else if (statement instanceof PsiBreakStatement) {
      final PsiBreakStatement breakStatement = (PsiBreakStatement)statement;
      checkBreakStatement(breakStatement, definiteAssignment);
    }
    else if (statement instanceof PsiContinueStatement) {
      final PsiContinueStatement continueStatement = (PsiContinueStatement)statement;
      checkContinueStatement(continueStatement, definiteAssignment);
    }
    else if (statement instanceof PsiDeclarationStatement) {
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      checkDeclarationStatement(declarationStatement, definiteAssignment);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      final PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)statement;
      checkDoWhileStatement(doWhileStatement, definiteAssignment);
    }
    else if (statement instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      checkExpression(expressionStatement.getExpression(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    }
    else if (statement instanceof PsiExpressionListStatement) {
      final PsiExpressionListStatement expressionListStatement = (PsiExpressionListStatement)statement;
      checkExpressionListStatement(expressionListStatement, definiteAssignment);
    }
    else if (statement instanceof PsiForeachStatement) {
      final PsiForeachStatement foreachStatement = (PsiForeachStatement)statement;
      checkForeachStatement(foreachStatement, definiteAssignment);
    }
    else if (statement instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)statement;
      checkForStatement(forStatement, definiteAssignment);
    }
    else if (statement instanceof PsiIfStatement) {
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      checkIfStatement(ifStatement, definiteAssignment);
    }
    else if (statement instanceof PsiLabeledStatement) {
      final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)statement;
      checkLabeledStatement(labeledStatement, definiteAssignment);
    }
    else if (statement instanceof PsiReturnStatement) {
      final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      checkReturnStatement(returnStatement, definiteAssignment);
    }
    else if (statement instanceof PsiSwitchStatement) {
      final PsiSwitchStatement switchStatement = (PsiSwitchStatement)statement;
      checkSwitchStatement(switchStatement, definiteAssignment);
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      final PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)statement;
      checkSynchronizedStatement(synchronizedStatement, definiteAssignment);
    }
    else if (statement instanceof PsiThrowStatement) {
      final PsiThrowStatement throwStatement = (PsiThrowStatement)statement;
      checkThrowStatement(throwStatement, definiteAssignment);
    }
    else if (statement instanceof PsiTryStatement) {
      final PsiTryStatement tryStatement = (PsiTryStatement)statement;
      checkTryStatement(tryStatement, definiteAssignment);
    }
    else if (statement instanceof PsiWhileStatement) {
      final PsiWhileStatement whileStatement = (PsiWhileStatement)statement;
      checkWhileStatement(whileStatement, definiteAssignment);
    }
  }

  private static void checkAssertStatement(PsiAssertStatement assertStatement, DefiniteAssignment definiteAssignment) {
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    final boolean du = definiteAssignment.isDefinitelyUnassigned();
    final PsiExpression condition = assertStatement.getAssertCondition();
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
    final boolean resultDu = du && definiteAssignment.isDefinitelyUnassigned();
    definiteAssignment.set(da, du);
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
    checkExpression(assertStatement.getAssertDescription(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    definiteAssignment.set(da, resultDu);
  }

  private static void checkBreakStatement(PsiBreakStatement breakStatement, DefiniteAssignment definiteAssignment) {
    definiteAssignment.storeBeforeBreakStatement(breakStatement);
    definiteAssignment.set(true, true);
  }

  private static void checkContinueStatement(PsiContinueStatement continueStatement, DefiniteAssignment definiteAssignment) {
    definiteAssignment.storeBeforeContinueStatement(continueStatement);
    definiteAssignment.set(true, true);
  }

  private static void checkDeclarationStatement(PsiDeclarationStatement declarationStatement, DefiniteAssignment definiteAssignment) {
    for (PsiElement element : declarationStatement.getDeclaredElements()) {
      if (element instanceof PsiLocalVariable) {
        final PsiLocalVariable variable = (PsiLocalVariable)element;
        checkLocalVariable(variable, definiteAssignment);
      }
      else if (element instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)element;
        final boolean da = definiteAssignment.isDefinitelyAssigned();
        final boolean du = definiteAssignment.isDefinitelyUnassigned();
        definiteAssignment.set(true, false);
        checkClass(aClass, definiteAssignment);
        definiteAssignment.set(da, du);
      }
      else {
        throw new AssertionError("unknown element declared: " + element);
      }
    }
  }

  private static void checkDoWhileStatement(PsiDoWhileStatement doWhileStatement, DefiniteAssignment definiteAssignment) {
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    final boolean du = definiteAssignment.isDefinitelyUnassigned();
    final PsiExpression condition = doWhileStatement.getCondition();
    final PsiStatement body = doWhileStatement.getBody();
    checkStatement(body, definiteAssignment);
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
    definiteAssignment.set(da, definiteAssignment.isDefinitelyUnassigned() & du);
    checkStatement(body, definiteAssignment);
    definiteAssignment.andDefiniteAssignmentBeforeContinue(doWhileStatement);
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
    definiteAssignment.andDefiniteAssignmentBeforeBreak(doWhileStatement);
  }

  private static void checkExpressionListStatement(PsiExpressionListStatement expressionListStatement,
                                                   DefiniteAssignment definiteAssignment) {
    final PsiExpressionList expressionList = expressionListStatement.getExpressionList();
    for (PsiExpression expression : expressionList.getExpressions()) {
      checkExpression(expression, definiteAssignment, BooleanExpressionValue.UNDEFINED);
    }
  }

  private static void checkForeachStatement(PsiForeachStatement foreachStatement, DefiniteAssignment definiteAssignment) {
    checkExpression(foreachStatement.getIteratedValue(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    checkStatement(foreachStatement.getBody(), definiteAssignment);
    definiteAssignment.set(da, definiteAssignment.isDefinitelyUnassigned());
    definiteAssignment.andDefiniteAssignmentBeforeBreak(foreachStatement);
  }

  private static void checkForStatement(PsiForStatement forStatement, DefiniteAssignment definiteAssignment) {
    checkStatement(forStatement.getInitialization(), definiteAssignment);
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    final boolean du = definiteAssignment.isDefinitelyUnassigned();
    final PsiExpression condition = forStatement.getCondition();
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
    checkStatement(forStatement.getBody(), definiteAssignment);
    definiteAssignment.andDefiniteAssignmentBeforeContinue(forStatement);
    checkStatement(forStatement.getUpdate(), definiteAssignment);
    // hack because javac does not match spec for incrementation part of for statement
    checkStatement(forStatement.getBody(), definiteAssignment);
    definiteAssignment.set(da, du);
    if (condition == null) {
      definiteAssignment.set(true, true);
    }
    else {
      checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
    }
    definiteAssignment.andDefiniteAssignmentBeforeBreak(forStatement);
  }

  private static void checkIfStatement(PsiIfStatement ifStatement, DefiniteAssignment definiteAssignment) {
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    final boolean du = definiteAssignment.isDefinitelyUnassigned();
    final PsiExpression condition = ifStatement.getCondition();
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
    checkStatement(ifStatement.getThenBranch(), definiteAssignment);
    final boolean resultDa = definiteAssignment.isDefinitelyAssigned();
    final boolean resultDu = definiteAssignment.isDefinitelyUnassigned();
    definiteAssignment.set(da, du);
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
    checkStatement(ifStatement.getElseBranch(), definiteAssignment);
    definiteAssignment.and(resultDa, resultDu);
  }

  private static void checkLabeledStatement(PsiLabeledStatement labeledStatement, DefiniteAssignment definiteAssignment) {
    final PsiStatement statement = labeledStatement.getStatement();
    checkStatement(statement, definiteAssignment);
    definiteAssignment.andDefiniteAssignmentBeforeBreak(statement);
  }

  private static void checkReturnStatement(PsiReturnStatement returnStatement, DefiniteAssignment definiteAssignment) {
    definiteAssignment.storeBeforeReturn(returnStatement);
    checkExpression(returnStatement.getReturnValue(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    definiteAssignment.set(true, true);
  }

  private static void checkSwitchStatement(PsiSwitchStatement switchStatement, DefiniteAssignment definiteAssignment) {
    checkExpression(switchStatement.getExpression(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    final PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return;
    }
    boolean defaultSeen = false;
    for (final PsiStatement statement : statements) {
      if (statement instanceof PsiSwitchLabelStatement) {
        final PsiSwitchLabelStatement switchLabelStatement = (PsiSwitchLabelStatement)statement;
        if (switchLabelStatement.isDefaultCase()) {
          defaultSeen = true;
        }
      }
      checkStatement(statement, definiteAssignment);
    }
    definiteAssignment.andDefiniteAssignmentBeforeBreak(switchStatement); // enum switch not specified
    definiteAssignment.and(defaultSeen, definiteAssignment.isDefinitelyUnassigned());
  }

  private static void checkSynchronizedStatement(PsiSynchronizedStatement synchronizedStatement, DefiniteAssignment definiteAssignment) {
    checkExpression(synchronizedStatement.getLockExpression(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    checkCodeBlock(synchronizedStatement.getBody(), definiteAssignment);
  }

  private static void checkThrowStatement(PsiThrowStatement throwStatement, DefiniteAssignment definiteAssignment) {
    checkExpression(throwStatement.getException(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    definiteAssignment.set(true, true);
  }

  private static void checkTryStatement(PsiTryStatement tryStatement, DefiniteAssignment definiteAssignment) {
    // try with resources not specified in JLS Java SE 8 Edition chapter 16
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      for (PsiResourceListElement element : resourceList) {
        if (element instanceof PsiResourceExpression) {
          final PsiResourceExpression resourceExpression = (PsiResourceExpression)element;
          checkExpression(resourceExpression.getExpression(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
        }
        else if (element instanceof PsiResourceVariable) {
          final PsiResourceVariable resourceVariable = (PsiResourceVariable)element;
          checkExpression(resourceVariable.getInitializer(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
        }
        else {
          throw new AssertionError();
        }
      }
    }
    checkCodeBlock(tryStatement.getTryBlock(), definiteAssignment);
    final boolean du = definiteAssignment.isDefinitelyUnassigned();
    boolean resultDa = definiteAssignment.isDefinitelyAssigned();
    boolean resultDu = du;
    for (PsiCodeBlock catchBlock : tryStatement.getCatchBlocks()) {
      definiteAssignment.set(da, du);
      checkCodeBlock(catchBlock, definiteAssignment);
      resultDu &= definiteAssignment.isDefinitelyUnassigned();
      resultDa &= definiteAssignment.isDefinitelyAssigned();
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      definiteAssignment.set(da, definiteAssignment.isDefinitelyUnassigned());
      checkCodeBlock(finallyBlock, definiteAssignment);
      resultDu &= definiteAssignment.isDefinitelyUnassigned(); // spec problem
    }
    definiteAssignment.set(resultDa | definiteAssignment.isDefinitelyAssigned(), resultDu);
  }

  private static void checkWhileStatement(PsiWhileStatement whileStatement, DefiniteAssignment definiteAssignment) {
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    final PsiExpression condition = whileStatement.getCondition();
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
    checkStatement(whileStatement.getBody(), definiteAssignment);
    definiteAssignment.andDefiniteAssignmentBeforeContinue(whileStatement);
    definiteAssignment.set(da, definiteAssignment.isDefinitelyUnassigned());
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
    definiteAssignment.andDefiniteAssignmentBeforeBreak(whileStatement);
  }

  private static void checkExpression(@Nullable PsiExpression expression,
                                      DefiniteAssignment definiteAssignment,
                                      BooleanExpressionValue value) {
    if (expression == null || definiteAssignment.stop()) {
      return;
    }
    if (PsiType.BOOLEAN.equals(expression.getType())) {
      final Object result = ExpressionUtils.computeConstantExpression(expression);
      if (result != null) {
        if (Boolean.TRUE == result && BooleanExpressionValue.WHEN_FALSE == value ||
            Boolean.FALSE == result && BooleanExpressionValue.WHEN_TRUE == value) {
          definiteAssignment.set(true, true);
        }
        return;
      }
    }
    if (expression instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)expression;
      checkArrayAccessExpression(arrayAccessExpression, definiteAssignment, value);
    }
    else if (expression instanceof PsiArrayInitializerExpression) {
      final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)expression;
      checkArrayInitializerExpression(arrayInitializerExpression, definiteAssignment, value);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      checkAssignmentExpression(assignmentExpression, definiteAssignment);
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      checkConditionalExpression(conditionalExpression, definiteAssignment, value);
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
      checkExpression(instanceOfExpression.getOperand(), definiteAssignment, value);
    }
    else if (expression instanceof PsiLambdaExpression) {
      final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)expression;
      final boolean du = definiteAssignment.isDefinitelyUnassigned();
      definiteAssignment.set(definiteAssignment.isDefinitelyAssigned(), false);
      checkLambdaExpression(lambdaExpression, definiteAssignment);
      definiteAssignment.set(definiteAssignment.isDefinitelyAssigned(), du);
    }
    else if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      checkMethodCallExpression(methodCallExpression, definiteAssignment, value);
    }
    else if (expression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      checkNewExpression(newExpression, definiteAssignment, value);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      checkExpression(parenthesizedExpression.getExpression(), definiteAssignment, value);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      checkPolyadicExpression(polyadicExpression, definiteAssignment, value);
    }
    else if (expression instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)expression;
      checkPostFixExpression(postfixExpression, definiteAssignment);
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      checkPrefixExpression(prefixExpression, definiteAssignment, value);
    }
    else if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      checkReferenceExpression(referenceExpression, definiteAssignment, value);
    }
    else if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
      checkExpression(typeCastExpression.getOperand(), definiteAssignment, value);
    }
  }

  private static void checkArrayAccessExpression(PsiArrayAccessExpression arrayAccessExpression,
                                                 DefiniteAssignment definiteAssignment,
                                                 BooleanExpressionValue value) {
    checkExpression(arrayAccessExpression.getArrayExpression(), definiteAssignment, value);
    checkExpression(arrayAccessExpression.getIndexExpression(), definiteAssignment, value);
  }

  private static void checkArrayInitializerExpression(PsiArrayInitializerExpression arrayInitializerExpression,
                                                      DefiniteAssignment definiteAssignment,
                                                      BooleanExpressionValue value) {
    for (PsiExpression initializer : arrayInitializerExpression.getInitializers()) {
      checkExpression(initializer, definiteAssignment, value);
    }
  }

  private static void checkAssignmentExpression(PsiAssignmentExpression assignmentExpression, DefiniteAssignment definiteAssignment) {
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final PsiReferenceExpression referenceExpression = getReferenceIfAssignmentOfVariable(lhs, definiteAssignment);
    if (referenceExpression == null) {
      checkExpression(lhs, definiteAssignment, BooleanExpressionValue.UNDEFINED);
      checkExpression(assignmentExpression.getRExpression(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    }
    else {
      final IElementType tokenType = assignmentExpression.getOperationTokenType();
      if (!JavaTokenType.EQ.equals(tokenType)) {
        definiteAssignment.valueAccess(referenceExpression);
      }
      checkExpression(assignmentExpression.getRExpression(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
      definiteAssignment.assign(referenceExpression, true);
    }
  }

  private static PsiReferenceExpression getReferenceIfAssignmentOfVariable(PsiExpression lhs, DefiniteAssignment definiteAssignment) {
    lhs = ParenthesesUtils.stripParentheses(lhs);
    if (!(lhs instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
    final PsiElement target = referenceExpression.resolve();
    if (!definiteAssignment.getVariable().equals(target)) {
      return null;
    }
    final PsiExpression qualifier = referenceExpression.getQualifierExpression();
    if (qualifier != null && (!(qualifier instanceof PsiThisExpression) || ((PsiThisExpression)qualifier).getQualifier() != null)) {
      return null;
    }
    return referenceExpression;
  }

  private static void checkConditionalExpression(PsiConditionalExpression conditionalExpression,
                                                 DefiniteAssignment definiteAssignment,
                                                 BooleanExpressionValue value) {
    final PsiExpression thenExpression = conditionalExpression.getThenExpression();
    final PsiExpression elseExpression = conditionalExpression.getElseExpression();
    if (thenExpression != null && PsiType.BOOLEAN.equals(thenExpression.getType()) &&
        elseExpression != null && PsiType.BOOLEAN.equals(elseExpression.getType())) {
      if (value == BooleanExpressionValue.WHEN_TRUE) {
        checkConditionalInternal(conditionalExpression, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
      }
      else if (value == BooleanExpressionValue.WHEN_FALSE) {
        checkConditionalInternal(conditionalExpression, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
      }
      else {
        final boolean da = definiteAssignment.isDefinitelyAssigned();
        final boolean du = definiteAssignment.isDefinitelyUnassigned();
        checkConditionalExpression(conditionalExpression, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
        final boolean resultDa = definiteAssignment.isDefinitelyAssigned();
        final boolean resultDu = definiteAssignment.isDefinitelyUnassigned();
        definiteAssignment.set(da, du);
        checkConditionalExpression(conditionalExpression, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
        definiteAssignment.and(resultDa, resultDu);
      }
    }
    else {
      checkConditionalInternal(conditionalExpression, definiteAssignment, BooleanExpressionValue.UNDEFINED);
    }
  }

  private static void checkConditionalInternal(PsiConditionalExpression conditionalExpression,
                                               DefiniteAssignment definiteAssignment,
                                               BooleanExpressionValue value) {
    final boolean da = definiteAssignment.isDefinitelyAssigned();
    final boolean du = definiteAssignment.isDefinitelyUnassigned();
    final PsiExpression condition = conditionalExpression.getCondition();
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
    checkExpression(conditionalExpression.getThenExpression(), definiteAssignment, value);
    final boolean resultDa = definiteAssignment.isDefinitelyAssigned();
    final boolean resultDu = definiteAssignment.isDefinitelyUnassigned();
    definiteAssignment.set(da, du);
    checkExpression(condition, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
    checkExpression(conditionalExpression.getElseExpression(), definiteAssignment, value);
    definiteAssignment.and(resultDa, resultDu);
  }

  private static void checkLambdaExpression(PsiLambdaExpression lambdaExpression, DefiniteAssignment definiteAssignment) {
    final PsiElement body = lambdaExpression.getBody();
    if (body instanceof PsiExpression) {
      final PsiExpression bodyExpression = (PsiExpression)body;
      checkExpression(bodyExpression, definiteAssignment, BooleanExpressionValue.UNDEFINED);
    }
    else if (body instanceof PsiCodeBlock) {
      final PsiCodeBlock codeBlock = (PsiCodeBlock)body;
      checkCodeBlock(codeBlock, definiteAssignment);
    }
  }

  private static void checkMethodCallExpression(PsiMethodCallExpression methodCallExpression,
                                                DefiniteAssignment definiteAssignment,
                                                BooleanExpressionValue value) {
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    checkExpression(qualifier, definiteAssignment, value);
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    for (PsiExpression argument : argumentList.getExpressions()) {
      checkExpression(argument, definiteAssignment, value);
    }
  }

  private static void checkNewExpression(PsiNewExpression newExpression,
                                         DefiniteAssignment definiteAssignment,
                                         BooleanExpressionValue value) {
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList != null) {
      for (PsiExpression argument : argumentList.getExpressions()) {
        checkExpression(argument, definiteAssignment, value);
      }
    }
    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
    if (anonymousClass != null) {
      final boolean du = definiteAssignment.isDefinitelyUnassigned();
      definiteAssignment.set(definiteAssignment.isDefinitelyAssigned(), false);
      checkAnonymousClass(anonymousClass, definiteAssignment);
      definiteAssignment.set(definiteAssignment.isDefinitelyAssigned(), du);
    }
    for (PsiExpression dimension : newExpression.getArrayDimensions()) {
      checkExpression(dimension, definiteAssignment, value);
    }
    final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
    if (arrayInitializer != null) {
      checkExpression(arrayInitializer, definiteAssignment, value);
    }
  }

  private static void checkPolyadicExpression(PsiPolyadicExpression polyadicExpression,
                                              DefiniteAssignment definiteAssignment,
                                              BooleanExpressionValue value) {
    final PsiExpression[] operands = polyadicExpression.getOperands();
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    if (JavaTokenType.OROR == tokenType || JavaTokenType.ANDAND == tokenType) {
      for (int i = 1; i < operands.length; i++) {
        checkBinaryExpression(operands[i - 1], operands[i], tokenType == JavaTokenType.ANDAND, definiteAssignment, value);
      }
    }
    else {
      for (PsiExpression operand : operands) {
        checkExpression(operand, definiteAssignment, BooleanExpressionValue.UNDEFINED);
      }
    }
  }

  private static void checkBinaryExpression(PsiExpression lhs,
                                            PsiExpression rhs,
                                            boolean and,
                                            DefiniteAssignment definiteAssignment,
                                            BooleanExpressionValue value) {
    if (and ? value == BooleanExpressionValue.WHEN_FALSE : value == BooleanExpressionValue.WHEN_TRUE) {
      final boolean da = definiteAssignment.isDefinitelyAssigned();
      final boolean du = definiteAssignment.isDefinitelyUnassigned();
      checkExpression(lhs, definiteAssignment, and ? BooleanExpressionValue.WHEN_FALSE : BooleanExpressionValue.WHEN_TRUE);
      final boolean resultDa = definiteAssignment.isDefinitelyAssigned();
      final boolean resultDu = definiteAssignment.isDefinitelyUnassigned();
      definiteAssignment.set(da, du);
      checkExpression(lhs, definiteAssignment, and ? BooleanExpressionValue.WHEN_TRUE : BooleanExpressionValue.WHEN_FALSE);
      checkExpression(rhs, definiteAssignment, and ? BooleanExpressionValue.WHEN_FALSE : BooleanExpressionValue.WHEN_TRUE);
      definiteAssignment.and(resultDa, resultDu);
    }
    else if (and ? value == BooleanExpressionValue.WHEN_TRUE : value == BooleanExpressionValue.WHEN_FALSE) {
      checkExpression(lhs, definiteAssignment, and ? BooleanExpressionValue.WHEN_TRUE : BooleanExpressionValue.WHEN_FALSE);
      checkExpression(rhs, definiteAssignment, and ? BooleanExpressionValue.WHEN_TRUE : BooleanExpressionValue.WHEN_FALSE);
    }
    else {
      final boolean da = definiteAssignment.isDefinitelyAssigned();
      final boolean du = definiteAssignment.isDefinitelyUnassigned();
      checkBinaryExpression(lhs, rhs, false, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
      final boolean resultDa = definiteAssignment.isDefinitelyAssigned();
      final boolean resultDu = definiteAssignment.isDefinitelyUnassigned();
      definiteAssignment.set(da, du);
      checkBinaryExpression(lhs, rhs, false, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
      definiteAssignment.and(resultDa, resultDu);
    }
  }

  private static void checkPostFixExpression(PsiPostfixExpression postfixExpression, DefiniteAssignment definiteAssignment) {
    checkExpression(postfixExpression.getOperand(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
  }

  private static void checkPrefixExpression(PsiPrefixExpression prefixExpression,
                                            DefiniteAssignment definiteAssignment,
                                            BooleanExpressionValue value) {
    final IElementType tokenType = prefixExpression.getOperationTokenType();
    if (JavaTokenType.EXCL == tokenType) {
      if (value == BooleanExpressionValue.WHEN_TRUE) {
        checkExpression(prefixExpression.getOperand(), definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
      }
      else if (value == BooleanExpressionValue.WHEN_FALSE) {
        checkExpression(prefixExpression.getOperand(), definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
      }
      else {
        final boolean da = definiteAssignment.isDefinitelyAssigned();
        final boolean du = definiteAssignment.isDefinitelyUnassigned();
        checkPrefixExpression(prefixExpression, definiteAssignment, BooleanExpressionValue.WHEN_TRUE);
        final boolean resultDa = definiteAssignment.isDefinitelyAssigned();
        final boolean resultDu = definiteAssignment.isDefinitelyUnassigned();
        definiteAssignment.set(da, du);
        checkPrefixExpression(prefixExpression, definiteAssignment, BooleanExpressionValue.WHEN_FALSE);
        definiteAssignment.and(resultDa, resultDu);
      }
    }
    else {
      checkExpression(prefixExpression.getOperand(), definiteAssignment, BooleanExpressionValue.UNDEFINED);
    }
  }

  private static void checkReferenceExpression(PsiReferenceExpression referenceExpression,
                                               DefiniteAssignment definiteAssignment,
                                               BooleanExpressionValue value) {
    final PsiExpression qualifier = referenceExpression.getQualifierExpression();
    if (qualifier != null) {
      checkExpression(qualifier, definiteAssignment, value);
    }
    if (!definiteAssignment.getVariable().equals(referenceExpression.resolve())) {
      return;
    }
    if (PsiUtil.isAccessedForWriting(referenceExpression)) {
      definiteAssignment.assign(referenceExpression, false);
    }
    else {
      if (qualifier != null) {
        if (!(qualifier instanceof PsiThisExpression)) {
          return;
        }
        final PsiThisExpression thisExpression = (PsiThisExpression)qualifier;
        if (thisExpression.getQualifier() != null) {
          return;
        }
      }
      definiteAssignment.valueAccess(referenceExpression);
    }
  }

  private enum BooleanExpressionValue {
    WHEN_TRUE, WHEN_FALSE, UNDEFINED
  }
}
