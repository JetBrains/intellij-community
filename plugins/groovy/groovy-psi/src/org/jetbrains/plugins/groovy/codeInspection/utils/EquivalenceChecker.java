/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.utils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

@SuppressWarnings({"OverlyComplexMethod",
    "MethodWithMultipleLoops",
    "OverlyComplexMethod",
    "OverlyLongMethod",
    "SwitchStatementWithTooManyBranches",
    "SwitchStatement",
    "OverlyComplexClass",
    "ClassWithTooManyMethods"})
public class EquivalenceChecker {

  private EquivalenceChecker() {
    super();
  }

  private static final int LITERAL_EXPRESSION = 1;
  private static final int REFERENCE_EXPRESSION = 3;
  private static final int CALL_EXPRESSION = 5;
  private static final int NEW_EXPRESSION = 6;
  private static final int ARRAY_LITERAL_EXPRESSION = 7;
  private static final int CLOSABLE_BLOCK_EXPRESSION = 8;
  private static final int PREFIX_EXPRESSION = 10;
  private static final int POSTFIX_EXPRESSION = 11;
  private static final int BINARY_EXPRESSION = 12;
  private static final int CONDITIONAL_EXPRESSION = 13;
  private static final int ASSIGNMENT_EXPRESSION = 14;
  private static final int ELVIS_EXPRESSION = 15;
  private static final int TYPE_CAST_EXPRESSION = 16;
  private static final int SAFE_CAST_EXPRESSION = 17;
  private static final int INSTANCEOF_EXPRESSION = 18;
  private static final int RANGE_EXPRESSION = 19;
  private static final int LIST_OR_MAP_EXPRESSION = 20;
  private static final int INDEX_EXPRESSION = 21;
  private static final int PROPERTY_SELECTION_EXPRESSION = 22;

  private static final int BLOCK_STATEMENT = 1;
  private static final int BREAK_STATEMENT = 2;
  private static final int CONTINUE_STATEMENT = 3;
  private static final int VAR_STATEMENT = 4;
  private static final int EMPTY_STATEMENT = 6;
  private static final int EXPRESSION_STATEMENT = 8;
  private static final int FOR_STATEMENT = 9;
  private static final int IF_STATEMENT = 10;
  private static final int RETURN_STATEMENT = 12;
  private static final int SWITCH_STATEMENT = 14;
  private static final int THROW_STATEMENT = 16;
  private static final int TRY_STATEMENT = 17;
  private static final int WHILE_STATEMENT = 18;
  private static final int SYNCHRONIZED_STATEMENT = 19;
  private static final int ASSERT_STATEMENT = 20;
  private static final int APPLICATION_STATEMENT = 21;

  public static boolean statementsAreEquivalent(@Nullable GrStatement exp1,
                                                @Nullable GrStatement exp2) {
    if (exp1 == null && exp2 == null) {
      return true;
    }
    if (exp1 == null || exp2 == null) {
      return false;
    }
    final int type1 = getStatementType(exp1);
    final int type2 = getStatementType(exp2);
    if (type1 != type2) {
      return false;
    }
    switch (type1) {
      case BLOCK_STATEMENT:
        return blockStatementsAreEquivalent((GrBlockStatement) exp1, (GrBlockStatement) exp2);
      case BREAK_STATEMENT:
        return true;
      case CONTINUE_STATEMENT:
        return true;
      case VAR_STATEMENT:
        return varStatementsAreEquivalent((GrVariableDeclaration) exp1, (GrVariableDeclaration) exp2);
      case EMPTY_STATEMENT:
        return true;
      case APPLICATION_STATEMENT:
        return applicationStatementsAreEquivalent((GrApplicationStatement) exp1, (GrApplicationStatement) exp2);
      case EXPRESSION_STATEMENT:
        return expressionStatementsAreEquivalent((GrExpression) exp1, (GrExpression) exp2);
      case FOR_STATEMENT:
        return forInStatementsAreEquivalent((GrForStatement) exp1, (GrForStatement) exp2);
      case IF_STATEMENT:
        return ifStatementsAreEquivalent((GrIfStatement) exp1, (GrIfStatement) exp2);
      case RETURN_STATEMENT:
        return returnStatementsAreEquivalent((GrReturnStatement) exp1, (GrReturnStatement) exp2);
      case SWITCH_STATEMENT:
        return switchStatementsAreEquivalent((GrSwitchStatement) exp1, (GrSwitchStatement) exp2);
      case THROW_STATEMENT:
        return throwStatementsAreEquivalent((GrThrowStatement) exp1, (GrThrowStatement) exp2);
      case TRY_STATEMENT:
        return tryStatementsAreEquivalent((GrTryCatchStatement) exp1, (GrTryCatchStatement) exp2);
      case WHILE_STATEMENT:
        return whileStatementsAreEquivalent((GrWhileStatement) exp1, (GrWhileStatement) exp2);
      case SYNCHRONIZED_STATEMENT:
        return synchronizedStatementsAreEquivalent((GrSynchronizedStatement) exp1, (GrSynchronizedStatement) exp2);
      case ASSERT_STATEMENT:
        return assertStatementsAreEquivalent((GrAssertStatement) exp1, (GrAssertStatement) exp2);
      default:
        return false;
    }
  }

  private static boolean applicationStatementsAreEquivalent(GrApplicationStatement statement1,
                                                            GrApplicationStatement statement2) {
    final GrExpression funExpression1 = statement1.getInvokedExpression();
    final GrExpression funExpression2 = statement2.getInvokedExpression();
    if (!expressionsAreEquivalent(funExpression1, funExpression2)) {
      return false;
    }

    final GrArgumentList argumentList1 = statement1.getArgumentList();
    if (argumentList1 == null) {
      return false;
    }
    final GrArgumentList argumentList2 = statement2.getArgumentList();
    if (argumentList2 == null) {
      return false;
    }
    final GrExpression[] args1 = argumentList1.getExpressionArguments();
    final GrExpression[] args2 = argumentList2.getExpressionArguments();
    if (!expressionListsAreEquivalent(args1, args2)) {
      return false;
    }
    final GrNamedArgument[] namedArgs1 = argumentList1.getNamedArguments();
    final GrNamedArgument[] namedArgs2 = argumentList2.getNamedArguments();
    if (!namedArgumentListsAreEquivalent(namedArgs1, namedArgs2)) {
      return false;
    }
    return true;
  }

  private static boolean assertStatementsAreEquivalent(GrAssertStatement statement1, GrAssertStatement statement2) {
    return expressionsAreEquivalent(statement1.getAssertion(), statement2.getAssertion()) &&
      expressionsAreEquivalent(statement1.getErrorMessage(), statement2.getErrorMessage());
  }

  private static boolean synchronizedStatementsAreEquivalent(GrSynchronizedStatement statement1,
                                                             GrSynchronizedStatement statement2) {
    return expressionsAreEquivalent(statement1.getMonitor(), statement2.getMonitor()) &&
        openBlocksAreEquivalent(statement1.getBody(), statement2.getBody());
  }

  private static boolean varStatementsAreEquivalent(@NotNull GrVariableDeclaration statement1,
                                                    @NotNull GrVariableDeclaration statement2) {
    final GrVariable[] variables1 = statement1.getVariables();
    final GrVariable[] variables2 = statement2.getVariables();
    if (variables1.length != variables2.length) {
      return false;
    }
    for (int i = 0; i < variables2.length; i++) {
      if (!variablesAreEquivalent(variables1[i], variables2[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean variablesAreEquivalent(@NotNull GrVariable var1,
                                                @NotNull GrVariable var2) {
    final GrExpression initializer1 = var1.getInitializerGroovy();
    final GrExpression initializer2 = var2.getInitializerGroovy();
    if (!expressionsAreEquivalent(initializer1, initializer2)) {
      return false;
    }
    final PsiType type1 = var1.getType();
    final PsiType type2 = var2.getType();
    if (!typesAreEquivalent(type1, type2)) {
      return false;
    }
    final String name1 = var1.getName();
    final String name2 = var2.getName();
    return name1.equals(name2);
  }

  private static boolean tryStatementsAreEquivalent(@NotNull GrTryCatchStatement statement1,
                                                    @NotNull GrTryCatchStatement statement2) {
    final GrOpenBlock tryBlock1 = statement1.getTryBlock();
    final GrOpenBlock tryBlock2 = statement2.getTryBlock();
    if (!openBlocksAreEquivalent(tryBlock1, tryBlock2)) {
      return false;
    }
    final GrFinallyClause finallyBlock1 = statement1.getFinallyClause();
    final GrFinallyClause finallyBlock2 = statement2.getFinallyClause();
    if (finallyBlock1 != null) {
      if (finallyBlock2 == null || !openBlocksAreEquivalent(finallyBlock1.getBody(), finallyBlock2.getBody())) {
        return false;
      }
    } else if (finallyBlock2 != null) {
      return false;
    }
    final GrCatchClause[] catchBlocks1 = statement1.getCatchClauses();
    final GrCatchClause[] catchBlocks2 = statement2.getCatchClauses();
    if (catchBlocks1.length != catchBlocks2.length) {
      return false;
    }
    for (int i = 0; i < catchBlocks2.length; i++) {
      if (!catchClausesAreEquivalent(catchBlocks1[i], catchBlocks2[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean catchClausesAreEquivalent(GrCatchClause clause1, GrCatchClause clause2) {
    return parametersAreEquivalent(clause1.getParameter(), clause2.getParameter()) &&
        openBlocksAreEquivalent(clause1.getBody(), clause2.getBody());
  }

  private static boolean parametersAreEquivalent(@Nullable GrParameter parameter1,
                                                 @Nullable GrParameter parameter2) {
    if (parameter1 == null || parameter2 == null) {
      return false;
    }
    final PsiType type1 = parameter1.getType();
    final PsiType type2 = parameter2.getType();
    if (!typesAreEquivalent(type1, type2)) {
      return false;
    }
    final String name1 = parameter1.getName();
    final String name2 = parameter2.getName();
    return name1.equals(name2);
  }

  private static boolean typesAreEquivalent(@Nullable PsiType type1, @Nullable PsiType type2) {
    if (type1 == null) {
      return type2 == null;
    }
    if (type2 == null) {
      return false;
    }
    return type1.equals(type2);
  }

  private static boolean whileStatementsAreEquivalent(@NotNull GrWhileStatement statement1,
                                                      @NotNull GrWhileStatement statement2) {
    final GrExpression condition1 = (GrExpression) statement1.getCondition();
    final GrExpression condition2 = (GrExpression) statement2.getCondition();
    final GrStatement body1 = statement1.getBody();
    final GrStatement body2 = statement2.getBody();
    return expressionsAreEquivalent(condition1, condition2) &&
        statementsAreEquivalent(body1, body2);
  }

  private static boolean forInStatementsAreEquivalent(@NotNull GrForStatement statement1,
                                                      @NotNull GrForStatement statement2) {
    final GrForClause clause1 = statement1.getClause();
    final GrForClause clause2 = statement2.getClause();
    if (!forClausesAreEquivalent(clause1, clause2)) {
      return false;
    }
    final GrStatement body1 = statement1.getBody();
    final GrStatement body2 = statement2.getBody();
    return statementsAreEquivalent(body1, body2);
  }

  private static boolean forClausesAreEquivalent(@Nullable GrForClause statement1,
                                                 @Nullable GrForClause statement2) {
    if (statement1 == null && statement2 == null) return true;
    if (statement1 == null || statement2 == null) return false;
    final GrVariable var1 = statement1.getDeclaredVariable();
    final GrVariable var2 = statement2.getDeclaredVariable();
    if (var1 == null && var2 == null) return true;
    if (var1 == null || var2 == null) return false;
    return variablesAreEquivalent(var1, var2);
  }

  private static boolean switchStatementsAreEquivalent(@NotNull GrSwitchStatement statement1,
                                                       @NotNull GrSwitchStatement statement2) {
    final GrExpression switchExpression1 = statement1.getCondition();
    final GrExpression switchExpression2 = statement2.getCondition();
    if (!expressionsAreEquivalent(switchExpression1, switchExpression2)) {
      return false;
    }
    final GrCaseSection[] clauses1 = statement1.getCaseSections();
    final GrCaseSection[] clauses2 = statement2.getCaseSections();
    if (clauses1.length != clauses2.length) {
      return false;
    }
    for (int i = 0; i < clauses1.length; i++) {
      final GrCaseSection clause1 = clauses1[i];
      final GrCaseSection clause2 = clauses2[i];
      if (!caseClausesAreEquivalent(clause1, clause2)) {
        return false;
      }
    }
    return true;
  }

  private static boolean caseClausesAreEquivalent(GrCaseSection clause1, GrCaseSection clause2) {
    final GrCaseLabel[] label1 = clause1.getCaseLabels();
    final GrCaseLabel[] label2 = clause2.getCaseLabels();
    if (label1.length != label2.length) return false;

    for (int i = 0; i < label1.length; i++) {
      GrCaseLabel l1 = label1[i];
      GrCaseLabel l2 = label2[i];

      if (!expressionsAreEquivalent(l1.getValue(), l2.getValue())) {
        return false;
      }
    }

    final GrStatement[] statements1 = clause1.getStatements();
    final GrStatement[] statements2 = clause2.getStatements();
    if (statements1.length != statements2.length) {
      return false;
    }
    for (int i = 0; i < statements1.length; i++) {
      if (!statementsAreEquivalent(statements1[i], statements2[i])) {
        return false;
      }
    }
    return false;
  }

  private static boolean blockStatementsAreEquivalent(@NotNull GrBlockStatement statement1,
                                                      @NotNull GrBlockStatement statement2) {
    final GrOpenBlock block1 = statement1.getBlock();
    final GrOpenBlock block2 = statement2.getBlock();
    return openBlocksAreEquivalent(block1, block2);
  }

  private static boolean openBlocksAreEquivalent(@Nullable GrOpenBlock block1, @Nullable GrOpenBlock block2) {
    if (block1 == null || block2 == null) return false;

    final GrStatement[] statements1 = block1.getStatements();
    final GrStatement[] statements2 = block2.getStatements();
    if (statements1.length != statements2.length) {
      return false;
    }
    for (int i = 0; i < statements1.length; i++) {
      if (!statementsAreEquivalent(statements1[i], statements2[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean ifStatementsAreEquivalent(@NotNull GrIfStatement statement1,
                                                   @NotNull GrIfStatement statement2) {
    final GrExpression condition1 = statement1.getCondition();
    final GrExpression condition2 = statement2.getCondition();
    final GrStatement thenBranch1 = statement1.getThenBranch();
    final GrStatement thenBranch2 = statement2.getThenBranch();
    final GrStatement elseBranch1 = statement1.getElseBranch();
    final GrStatement elseBranch2 = statement2.getElseBranch();
    return expressionsAreEquivalent(condition1, condition2) &&
        statementsAreEquivalent(thenBranch1, thenBranch2) &&
        statementsAreEquivalent(elseBranch1, elseBranch2);
  }

  private static boolean expressionStatementsAreEquivalent(@NotNull GrExpression statement1,
                                                           @NotNull GrExpression statement2) {
    return expressionsAreEquivalent(statement1, statement2);
  }

  private static boolean returnStatementsAreEquivalent(@NotNull GrReturnStatement statement1,
                                                       @NotNull GrReturnStatement statement2) {
    final GrExpression returnValue1 = statement1.getReturnValue();
    final GrExpression returnValue2 = statement2.getReturnValue();
    return expressionsAreEquivalent(returnValue1, returnValue2);
  }

  private static boolean throwStatementsAreEquivalent(@NotNull GrThrowStatement statement1,
                                                      @NotNull GrThrowStatement statement2) {
    final GrExpression exception1 = statement1.getException();
    final GrExpression exception2 = statement2.getException();
    return expressionsAreEquivalent(exception1, exception2);
  }

  @SuppressWarnings({"ConstantConditions"})
  public static boolean expressionsAreEquivalent(@Nullable GrExpression exp1,
                                                 @Nullable GrExpression exp2) {
    if (exp1 == null && exp2 == null) {
      return true;
    }
    if (exp1 == null || exp2 == null) {
      return false;
    }
    GrExpression expToCompare1 = (GrExpression)PsiUtil.skipParentheses(exp1, false);
    GrExpression expToCompare2 = (GrExpression)PsiUtil.skipParentheses(exp2, false);
    final int type1 = getExpressionType(expToCompare1);
    final int type2 = getExpressionType(expToCompare2);
    if (type1 != type2) {
      return false;
    }
    switch (type1) {
      case LITERAL_EXPRESSION:
      case REFERENCE_EXPRESSION:
        final String text1 = expToCompare1.getText();
        final String text2 = expToCompare2.getText();
        return text1.equals(text2);
      case CALL_EXPRESSION:
        return methodCallExpressionsAreEquivalent((GrMethodCall) expToCompare1,
            (GrMethodCall) expToCompare2);
      case NEW_EXPRESSION:
        return newExpressionsAreEquivalent((GrNewExpression) expToCompare1,
            (GrNewExpression) expToCompare2);
      case ARRAY_LITERAL_EXPRESSION:
        return arrayDeclarationsAreEquivalent((GrArrayDeclaration) expToCompare1,
            (GrArrayDeclaration) expToCompare2);
      case PREFIX_EXPRESSION:
        return prefixExpressionsAreEquivalent((GrUnaryExpression) expToCompare1,
            (GrUnaryExpression) expToCompare2);
      case POSTFIX_EXPRESSION:
        return postfixExpressionsAreEquivalent((GrUnaryExpression) expToCompare1,
            (GrUnaryExpression) expToCompare2);
      case BINARY_EXPRESSION:
        return binaryExpressionsAreEquivalent((GrBinaryExpression) expToCompare1,
            (GrBinaryExpression) expToCompare2);
      case ASSIGNMENT_EXPRESSION:
        return assignmentExpressionsAreEquivalent((GrAssignmentExpression) expToCompare1,
            (GrAssignmentExpression) expToCompare2);
      case CONDITIONAL_EXPRESSION:
        return conditionalExpressionsAreEquivalent((GrConditionalExpression) expToCompare1,
            (GrConditionalExpression) expToCompare2);
      case ELVIS_EXPRESSION:
        return elvisExpressionsAreEquivalent((GrElvisExpression) expToCompare1,
            (GrElvisExpression) expToCompare2);
      case RANGE_EXPRESSION:
        return rangeExpressionsAreEquivalent((GrRangeExpression) expToCompare1,
            (GrRangeExpression) expToCompare2);
      case TYPE_CAST_EXPRESSION:
        return typecastExpressionsAreEquivalent((GrTypeCastExpression) expToCompare1,
            (GrTypeCastExpression) expToCompare2);
      case SAFE_CAST_EXPRESSION:
        return safeCastExpressionsAreEquivalent((GrSafeCastExpression)expToCompare1,
                                                (GrSafeCastExpression)expToCompare2);
      case INSTANCEOF_EXPRESSION:
        return instanceofExpressionsAreEquivalent((GrInstanceOfExpression) expToCompare1,
            (GrInstanceOfExpression) expToCompare2);
      case INDEX_EXPRESSION:
        return indexExpressionsAreEquivalent((GrIndexProperty) expToCompare1,
            (GrIndexProperty) expToCompare2);
      case LIST_OR_MAP_EXPRESSION:
        return listOrMapExpressionsAreEquivalent((GrListOrMap) expToCompare1,
            (GrListOrMap) expToCompare2);
      case CLOSABLE_BLOCK_EXPRESSION:
        return closableBlockExpressionsAreEquivalent((GrClosableBlock) expToCompare1,
            (GrClosableBlock) expToCompare2);
      case PROPERTY_SELECTION_EXPRESSION:
        return textOfExpressionsIsEquivalent(expToCompare1, expToCompare2); // todo
      default:
        return false;
    }
  }

  private static boolean textOfExpressionsIsEquivalent(GrExpression expToCompare1,
                                                       GrExpression expToCompare2) {
    final String text1 = expToCompare1.getText();
    final String text2 = expToCompare2.getText();
    return text1.equals(text2);
  }

  private static boolean closableBlockExpressionsAreEquivalent(GrClosableBlock closableBlock1,
                                                               GrClosableBlock closableBlock2) {
    final GrStatement[] statements1 = closableBlock1.getStatements();
    final GrStatement[] statements2 = closableBlock2.getStatements();
    if (statements1.length != statements2.length) {
      return false;
    }
    for (int i = 0; i < statements1.length; i++) {
      if (!statementsAreEquivalent(statements1[i], statements2[i])) {
        return false;
      }
    }

    GrParameter[] parameters1 = closableBlock1.getParameters();
    GrParameter[] parameters2 = closableBlock2.getParameters();
    return parametersAreEquivalent(parameters1, parameters2);
  }

  private static boolean parametersAreEquivalent(GrParameter[] parameters1, GrParameter[] parameters2) {
    if (parameters1.length != parameters2.length) return false;
    for (int i = 0; i < parameters1.length; i++) {
      if (!parametersAreEquivalent(parameters1[i], parameters2[i])) return false;
    }
    return true;
  }

  private static boolean listOrMapExpressionsAreEquivalent(GrListOrMap expression1, GrListOrMap expression2) {
    return expressionListsAreEquivalent(expression1.getInitializers(), expression2.getInitializers()) &&
        namedArgumentListsAreEquivalent(expression1.getNamedArguments(), expression2.getNamedArguments());
  }

  private static boolean arrayDeclarationsAreEquivalent(GrArrayDeclaration expression1,
                                                        GrArrayDeclaration expression2) {
    final int count1 = expression1.getArrayCount();
    final int count2 = expression2.getArrayCount();
    if (count1 != count2) {
      return false;
    }
    final GrExpression[] bounds1 = expression1.getBoundExpressions();
    final GrExpression[] bounds2 = expression2.getBoundExpressions();
    return expressionListsAreEquivalent(bounds1, bounds2);
  }

  private static boolean instanceofExpressionsAreEquivalent(GrInstanceOfExpression expression1,
                                                            GrInstanceOfExpression expression2) {
    final GrExpression operand1 = expression1.getOperand();
    final GrExpression operand2 = expression2.getOperand();
    if (!expressionsAreEquivalent(operand1, operand2)) {
      return false;
    }
    GrTypeElement typeElement1 = expression1.getTypeElement();
    GrTypeElement typeElement2 = expression2.getTypeElement();
    if (typeElement1 == null || typeElement2 == null) return false;

    final PsiType type1 = typeElement1.getType();
    final PsiType type2 = typeElement2.getType();
    return typesAreEquivalent(type1, type2);
  }

  private static boolean indexExpressionsAreEquivalent(GrIndexProperty expression1,
                                                       GrIndexProperty expression2) {
    return expressionsAreEquivalent(expression1.getInvokedExpression(), expression2.getInvokedExpression()) &&
           argumentListsAreEquivalent(expression1.getArgumentList(), expression2.getArgumentList());
  }

  private static boolean typecastExpressionsAreEquivalent(GrTypeCastExpression expression1,
                                                          GrTypeCastExpression expression2) {
    final GrExpression operand1 = expression1.getOperand();
    final GrExpression operand2 = expression2.getOperand();
    if (!expressionsAreEquivalent(operand1, operand2)) {
      return false;
    }
    final PsiType type1 = expression1.getCastTypeElement().getType();
    final PsiType type2 = expression2.getCastTypeElement().getType();
    return typesAreEquivalent(type1, type2);
  }

  private static boolean safeCastExpressionsAreEquivalent(GrSafeCastExpression expression1,
                                                          GrSafeCastExpression expression2) {
    final GrExpression operand1 = expression1.getOperand();
    final GrExpression operand2 = expression2.getOperand();
    if (!expressionsAreEquivalent(operand1, operand2)) {
      return false;
    }
    final GrTypeElement typeElement1 = expression1.getCastTypeElement();
    final GrTypeElement typeElement2 = expression2.getCastTypeElement();
    final PsiType safe1 = typeElement1 == null ? null : typeElement1.getType();
    final PsiType safe2 = typeElement2 == null ? null : typeElement2.getType();
    return typesAreEquivalent(safe1, safe2);
  }

  private static boolean methodCallExpressionsAreEquivalent(@NotNull GrMethodCall methodExp1,
                                                            @NotNull GrMethodCall methodExp2) {
    final GrExpression methodExpression1 = methodExp1.getInvokedExpression();
    final GrExpression methodExpression2 = methodExp2.getInvokedExpression();
    if (!expressionsAreEquivalent(methodExpression1, methodExpression2)) {
      return false;
    }
    final GrClosableBlock[] closures1 = methodExp1.getClosureArguments();
    final GrClosableBlock[] closures2 = methodExp2.getClosureArguments();
    if (!expressionListsAreEquivalent(closures1, closures2)) {
      return false;
    }

    return argumentListsAreEquivalent(methodExp1.getArgumentList(), methodExp2.getArgumentList());
  }

  private static boolean argumentListsAreEquivalent(@Nullable GrArgumentList list1, @Nullable GrArgumentList list2) {
    if (list1 == null && list2 == null) {
      return true;
    }
    if (list1 == null || list2 == null) {
      return false;
    }
    final GrExpression[] args1 = list1.getExpressionArguments();
    final GrExpression[] args2 = list2.getExpressionArguments();
    if (!expressionListsAreEquivalent(args1, args2)) {
      return false;
    }
    final GrNamedArgument[] namedArgs1 = list1.getNamedArguments();
    final GrNamedArgument[] namedArgs2 = list2.getNamedArguments();
    if (!namedArgumentListsAreEquivalent(namedArgs1, namedArgs2)) {
      return false;
    }
    return true;
  }

  private static boolean namedArgumentListsAreEquivalent(GrNamedArgument[] namedArgs1, GrNamedArgument[] namedArgs2) {
    if (namedArgs1.length != namedArgs2.length) {
      return false;
    }
    for (GrNamedArgument arg1 : namedArgs1) {
      final GrArgumentLabel label1 = arg1.getLabel();
      if (label1 == null) {
        return false;
      }
      final String name1 = label1.getName();
      boolean found = false;
      final GrExpression expression1 = arg1.getExpression();
      for (GrNamedArgument arg2 : namedArgs2) {
        final GrArgumentLabel label2 = arg2.getLabel();
        if (label2 == null) {
          return false;
        }
        final String name2 = label2.getName();
        final GrExpression expression2 = arg2.getExpression();
        if (name1 == null) {
          if (name2 == null &&
              expressionsAreEquivalent(((GrExpression)label1.getNameElement()), (GrExpression)label2.getNameElement()) &&
              expressionsAreEquivalent(expression1, expression2)) {
            found = true;
            break;
          }
        }
        else if (name1.equals(name2) && expressionsAreEquivalent(expression1, expression2)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  private static boolean newExpressionsAreEquivalent(@NotNull GrNewExpression newExp1,
                                                     @NotNull GrNewExpression newExp2) {
    final PsiMethod constructor1 = newExp1.resolveMethod();
    final PsiMethod constructor2 = newExp2.resolveMethod();
    if (constructor1 == null || constructor2 == null || constructor1.equals(constructor2)) {
      return false;
    }
    return argumentListsAreEquivalent(newExp1.getArgumentList(), newExp2.getArgumentList());
  }

  private static boolean prefixExpressionsAreEquivalent(@NotNull GrUnaryExpression prefixExp1,
                                                        @NotNull GrUnaryExpression prefixExp2) {
    final IElementType sign1 = prefixExp1.getOperationTokenType();
    final IElementType sign2 = prefixExp2.getOperationTokenType();
    if (sign1 != sign2) {
      return false;
    }
    final GrExpression operand1 = prefixExp1.getOperand();
    final GrExpression operand2 = prefixExp2.getOperand();
    return expressionsAreEquivalent(operand1, operand2);
  }

  private static boolean postfixExpressionsAreEquivalent(@NotNull GrUnaryExpression postfixExp1,
                                                         @NotNull GrUnaryExpression postfixExp2) {
    final IElementType sign1 = postfixExp1.getOperationTokenType();
    final IElementType sign2 = postfixExp2.getOperationTokenType();
    if (sign1 != sign2) {
      return false;
    }
    final GrExpression operand1 = postfixExp1.getOperand();
    final GrExpression operand2 = postfixExp2.getOperand();
    return expressionsAreEquivalent(operand1, operand2);
  }

  private static boolean binaryExpressionsAreEquivalent(@NotNull GrBinaryExpression binaryExp1,
                                                        @NotNull GrBinaryExpression binaryExp2) {
    final IElementType sign1 = binaryExp1.getOperationTokenType();
    final IElementType sign2 = binaryExp2.getOperationTokenType();
    if (sign1 != sign2) {
      return false;
    }
    final GrExpression lhs1 = binaryExp1.getLeftOperand();
    final GrExpression lhs2 = binaryExp2.getLeftOperand();
    final GrExpression rhs1 = binaryExp1.getRightOperand();
    final GrExpression rhs2 = binaryExp2.getRightOperand();
    return expressionsAreEquivalent(lhs1, lhs2)
        && expressionsAreEquivalent(rhs1, rhs2);
  }

  private static boolean rangeExpressionsAreEquivalent(@NotNull GrRangeExpression rangeExp1,
                                                       @NotNull GrRangeExpression rangeExp2) {
    return expressionsAreEquivalent(rangeExp1.getLeftOperand(), rangeExp2.getLeftOperand()) &&
           expressionsAreEquivalent(rangeExp1.getRightOperand(), rangeExp2.getRightOperand()) &&
           isInclusive(rangeExp1) == isInclusive(rangeExp2);
  }

  private static boolean isInclusive(GrRangeExpression range) {
    for (PsiElement child : range.getChildren()) {
      if ("..".equals(child.getText())) {
        return true;
      }
    }
    return false;
  }

  private static boolean assignmentExpressionsAreEquivalent(@NotNull GrAssignmentExpression assignExp1,
                                                            @NotNull GrAssignmentExpression assignExp2) {
    final IElementType sign1 = assignExp1.getOperationTokenType();
    final IElementType sign2 = assignExp2.getOperationTokenType();
    if (sign1 != sign2) {
      return false;
    }
    final GrExpression lhs1 = assignExp1.getLValue();
    final GrExpression lhs2 = assignExp2.getLValue();
    final GrExpression rhs1 = assignExp1.getRValue();
    final GrExpression rhs2 = assignExp2.getRValue();
    return expressionsAreEquivalent(lhs1, lhs2)
        && expressionsAreEquivalent(rhs1, rhs2);
  }

  private static boolean conditionalExpressionsAreEquivalent(@NotNull GrConditionalExpression condExp1,
                                                             @NotNull GrConditionalExpression condExp2) {
    final GrExpression condition1 = condExp1.getCondition();
    final GrExpression condition2 = condExp2.getCondition();
    final GrExpression thenExpression1 = condExp1.getThenBranch();
    final GrExpression thenExpression2 = condExp2.getThenBranch();
    final GrExpression elseExpression1 = condExp1.getElseBranch();
    final GrExpression elseExpression2 = condExp2.getElseBranch();
    return expressionsAreEquivalent(condition1, condition2) &&
           expressionsAreEquivalent(thenExpression1, thenExpression2) &&
           expressionsAreEquivalent(elseExpression1, elseExpression2);
  }

  private static boolean elvisExpressionsAreEquivalent(@NotNull GrElvisExpression condExp1,
                                                       @NotNull GrElvisExpression condExp2) {
    final GrExpression condition1 = condExp1.getCondition();
    final GrExpression condition2 = condExp2.getCondition();
    final GrExpression elseExpression1 = condExp1.getElseBranch();
    final GrExpression elseExpression2 = condExp2.getElseBranch();
    return expressionsAreEquivalent(condition1, condition2)
        && expressionsAreEquivalent(elseExpression1, elseExpression2);
  }

  private static boolean expressionListsAreEquivalent(@Nullable GrExpression[] expressions1,
                                                      @Nullable GrExpression[] expressions2) {
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

  private static int getExpressionType(@Nullable GrExpression exp) {
    if (exp instanceof GrArrayDeclaration) {
      return ARRAY_LITERAL_EXPRESSION;
    }
    if (exp instanceof GrLiteral) {
      return LITERAL_EXPRESSION;
    }
    if (exp instanceof GrReferenceExpression) {
      return REFERENCE_EXPRESSION;
    }
    if (exp instanceof GrTypeCastExpression) {
      return TYPE_CAST_EXPRESSION;
    }
    if (exp instanceof GrSafeCastExpression) {
      return SAFE_CAST_EXPRESSION;
    }
    if (exp instanceof GrInstanceOfExpression) {
      return INSTANCEOF_EXPRESSION;
    }
    if (exp instanceof GrNewExpression) {
      return NEW_EXPRESSION;
    }
    if (exp instanceof GrMethodCall) {
      return CALL_EXPRESSION;
    }
    if (exp instanceof GrUnaryExpression) {
      return ((GrUnaryExpression)exp).isPostfix() ? POSTFIX_EXPRESSION : PREFIX_EXPRESSION;
    }
    if (exp instanceof GrAssignmentExpression) {
      return ASSIGNMENT_EXPRESSION;
    }
    if (exp instanceof GrRangeExpression) {
      return RANGE_EXPRESSION;
    }
    if (exp instanceof GrBinaryExpression) {
      return BINARY_EXPRESSION;
    }
    if (exp instanceof GrElvisExpression) {
      return ELVIS_EXPRESSION;
    }
    if (exp instanceof GrConditionalExpression) {
      return CONDITIONAL_EXPRESSION;
    }
    if (exp instanceof GrIndexProperty) {
      return INDEX_EXPRESSION;
    }
    if (exp instanceof GrListOrMap) {
      return LIST_OR_MAP_EXPRESSION;
    }
    if (exp instanceof GrClosableBlock) {
      return CLOSABLE_BLOCK_EXPRESSION;
    }
    return -1; // Type of expression can be defined in third party plugins. See issue #IDEA-59846
  }

  private static int getStatementType(@Nullable GrStatement statement) {
    if (statement instanceof GrBlockStatement) {
      return BLOCK_STATEMENT;
    }
    if (statement instanceof GrBreakStatement) {
      return BREAK_STATEMENT;
    }
    if (statement instanceof GrContinueStatement) {
      return CONTINUE_STATEMENT;
    }
    if (statement instanceof GrVariableDeclaration) {
      return VAR_STATEMENT;
    }
    if (statement instanceof GrApplicationStatement) {
      return APPLICATION_STATEMENT;
    }
    if (statement instanceof GrExpression) {
      return EXPRESSION_STATEMENT;
    }
    if (statement instanceof GrForStatement) {
      return FOR_STATEMENT;
    }
    if (statement instanceof GrIfStatement) {
      return IF_STATEMENT;
    }
    if (statement instanceof GrReturnStatement) {
      return RETURN_STATEMENT;
    }
    if (statement instanceof GrSwitchStatement) {
      return SWITCH_STATEMENT;
    }
    if (statement instanceof GrThrowStatement) {
      return THROW_STATEMENT;
    }
    if (statement instanceof GrTryCatchStatement) {
      return TRY_STATEMENT;
    }
    if (statement instanceof GrWhileStatement) {
      return WHILE_STATEMENT;
    }
    if (statement instanceof GrSynchronizedStatement) {
      return SYNCHRONIZED_STATEMENT;
    }
    if (statement instanceof GrAssertStatement) {
      return ASSERT_STATEMENT;
    }

    return -1; // Type of expression can be defined in third party plugins. See issue #IDEA-59846
  }
}
