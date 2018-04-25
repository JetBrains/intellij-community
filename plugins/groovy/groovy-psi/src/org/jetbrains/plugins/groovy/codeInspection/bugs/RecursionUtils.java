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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.BoolUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

@SuppressWarnings({"OverlyComplexClass"})
class RecursionUtils {

  private RecursionUtils() {
    super();
  }

  public static boolean statementMayReturnBeforeRecursing(
      @Nullable GrStatement statement, GrMethod method) {
    if (statement == null) {
      return true;
    }
    if (statement instanceof GrBreakStatement ||
        statement instanceof GrContinueStatement ||
        statement instanceof GrThrowStatement ||
        statement instanceof GrExpression ||
        statement instanceof GrAssertStatement ||
        statement instanceof GrVariableDeclaration) {
      return false;
    } else if (statement instanceof GrReturnStatement) {
      final GrReturnStatement returnStatement =
          (GrReturnStatement) statement;
      final GrExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        if (expressionDefinitelyRecurses(returnValue, method)) {
          return false;
        }
      }
      return true;
    } else if (statement instanceof GrForStatement) {
      return forStatementMayReturnBeforeRecursing(
          (GrForStatement) statement, method);
    } else if (statement instanceof GrWhileStatement) {
      return whileStatementMayReturnBeforeRecursing(
          (GrWhileStatement) statement, method);
    } else if (statement instanceof GrSynchronizedStatement) {
      final GrCodeBlock body = ((GrSynchronizedStatement) statement)
          .getBody();
      return codeBlockMayReturnBeforeRecursing(body, method, false);
    } else if (statement instanceof GrBlockStatement) {
      final GrBlockStatement blockStatement =
          (GrBlockStatement) statement;
      final GrCodeBlock codeBlock = blockStatement.getBlock();
      return codeBlockMayReturnBeforeRecursing(codeBlock, method, false);
    } else if (statement instanceof GrIfStatement) {
      return ifStatementMayReturnBeforeRecursing(
          (GrIfStatement) statement, method);
    } else if (statement instanceof GrTryCatchStatement) {
      return tryStatementMayReturnBeforeRecursing(
          (GrTryCatchStatement) statement, method);
    } else if (statement instanceof GrSwitchStatement) {
      return switchStatementMayReturnBeforeRecursing(
          (GrSwitchStatement) statement, method);
    } else {
      // unknown statement type
      return true;
    }
  }

  private static boolean whileStatementMayReturnBeforeRecursing(
      GrWhileStatement loopStatement, GrMethod method) {
    final GrExpression condition = loopStatement.getCondition();
    if (condition == null) {
      return false;
    }
    if (expressionDefinitelyRecurses(condition, method)) {
      return false;
    }
    final GrStatement body = loopStatement.getBody();
    return statementMayReturnBeforeRecursing(body, method);
  }

  private static boolean forStatementMayReturnBeforeRecursing(
      GrForStatement loopStatement, GrMethod method) {
    final GrForClause forClause = loopStatement.getClause();
    if (forClause != null) {
      final GrVariable var = forClause.getDeclaredVariable();
      if (var != null) {
        final GrExpression initializer = var.getInitializerGroovy();
        if (expressionDefinitelyRecurses(initializer, method)) {
          return false;
        }
      }
    }
    final GrStatement body = loopStatement.getBody();
    return statementMayReturnBeforeRecursing(body, method);
  }

  private static boolean switchStatementMayReturnBeforeRecursing(
      GrSwitchStatement switchStatement, GrMethod method) {
    final GrCaseSection[] caseSections = switchStatement.getCaseSections();
    for (GrCaseSection caseSection : caseSections) {
      final GrStatement[] statements = caseSection.getStatements();
      for (final GrStatement statement : statements) {
        if (statementMayReturnBeforeRecursing(statement, method)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean tryStatementMayReturnBeforeRecursing(
      GrTryCatchStatement tryStatement, GrMethod method) {
    final GrFinallyClause finallyBlock = tryStatement.getFinallyClause();
    if (finallyBlock != null) {
      final GrOpenBlock body = finallyBlock.getBody();
      if (codeBlockMayReturnBeforeRecursing(body, method,
          false)) {
        return true;
      }
      if (codeBlockDefinitelyRecurses(body, method)) {
        return false;
      }
    }
    final GrCodeBlock tryBlock = tryStatement.getTryBlock();
    if (codeBlockMayReturnBeforeRecursing(tryBlock, method, false)) {
      return true;
    }
    final GrCatchClause[] catchBlocks = tryStatement.getCatchClauses();
    for (final GrCatchClause catchBlock : catchBlocks) {
      if (codeBlockMayReturnBeforeRecursing(catchBlock.getBody(), method, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ifStatementMayReturnBeforeRecursing(
      GrIfStatement ifStatement, GrMethod method) {
    GrExpression condition = ifStatement.getCondition();
    if (condition == null) return false;

    if (expressionDefinitelyRecurses(condition, method)) {
      return false;
    }
    final GrStatement thenBranch = ifStatement.getThenBranch();
    if (statementMayReturnBeforeRecursing(thenBranch, method)) {
      return true;
    }
    final GrStatement elseBranch = ifStatement.getElseBranch();
    return elseBranch != null &&
        statementMayReturnBeforeRecursing(elseBranch, method);
  }

  private static boolean codeBlockMayReturnBeforeRecursing(
      @Nullable GrCodeBlock block, GrMethod method, boolean endsInImplicitReturn) {
    if (block == null) {
      return true;
    }
    final GrStatement[] statements = block.getStatements();
    for (final GrStatement statement : statements) {
      if (statementMayReturnBeforeRecursing(statement, method)) {
        return true;
      }
      if (statementDefinitelyRecurses(statement, method)) {
        return false;
      }
    }
    return endsInImplicitReturn;
  }

  public static boolean methodMayRecurse(@NotNull GrMethod method) {
    final RecursionVisitor recursionVisitor = new RecursionVisitor(method);
    method.accept(recursionVisitor);
    return recursionVisitor.isRecursive();
  }

  private static boolean expressionDefinitelyRecurses(@Nullable GrExpression exp,
                                                      GrMethod method) {
    if (exp == null) {
      return false;
    }
    if (exp instanceof GrLiteral) {
      return false;
    }
    if (exp instanceof GrMethodCallExpression) {
      return methodCallExpressionDefinitelyRecurses(
          (GrMethodCallExpression) exp, method);
    }
    if (exp instanceof GrNewExpression) {
      return callExpressionDefinitelyRecurses(
          (GrNewExpression) exp, method);
    }
    if (exp instanceof GrAssignmentExpression) {
      return assignmentExpressionDefinitelyRecurses(
          (GrAssignmentExpression) exp, method);
    }
    if (exp instanceof GrArrayDeclaration) {
      return arrayInitializerExpressionDefinitelyRecurses(
          (GrArrayDeclaration) exp, method);
    }
    if (exp instanceof GrTypeCastExpression) {
      return typeCastExpressionDefinitelyRecurses(
          (GrTypeCastExpression) exp, method);
    }
    if (exp instanceof GrIndexProperty) {
      return arrayAccessExpressionDefinitelyRecurses((GrIndexProperty) exp, method);
    }
    if (exp instanceof GrUnaryExpression) {
      return unaryExpressionDefinitelyRecurses(
          (GrUnaryExpression) exp, method);
    }
    if (exp instanceof GrBinaryExpression) {
      return binaryExpressionDefinitelyRecurses(
          (GrBinaryExpression) exp, method);
    }
    if (exp instanceof GrInstanceOfExpression) {
      return instanceOfExpressionDefinitelyRecurses(
          (GrInstanceOfExpression) exp, method);
    }
    if (exp instanceof GrElvisExpression) {
      return elvisExpressionDefinitelyRecurses(
          (GrElvisExpression) exp, method);
    }
    if (exp instanceof GrConditionalExpression) {
      return conditionalExpressionDefinitelyRecurses(
          (GrConditionalExpression) exp, method);
    }
    if (exp instanceof GrParenthesizedExpression) {
      return parenthesizedExpressionDefinitelyRecurses(
          (GrParenthesizedExpression) exp, method);
    }
    if (exp instanceof GrReferenceExpression) {
      return referenceExpressionDefinitelyRecurses(
          (GrReferenceExpression) exp, method);
    }

    return false;
  }

  private static boolean conditionalExpressionDefinitelyRecurses(
      GrConditionalExpression expression, GrMethod method) {
    final GrExpression condExpression = expression.getCondition();
    if (expressionDefinitelyRecurses(condExpression, method)) {
      return true;
    }
    final GrExpression thenExpression = expression.getThenBranch();
    final GrExpression elseExpression = expression.getElseBranch();
    return expressionDefinitelyRecurses(thenExpression, method)
        && expressionDefinitelyRecurses(elseExpression, method);
  }

  private static boolean elvisExpressionDefinitelyRecurses(
      GrElvisExpression expression, GrMethod method) {
    final GrExpression condExpression = expression.getCondition();
    return expressionDefinitelyRecurses(condExpression, method);
  }

  private static boolean binaryExpressionDefinitelyRecurses(
      GrBinaryExpression expression, GrMethod method) {
    final GrExpression lhs = expression.getLeftOperand();
    if (expressionDefinitelyRecurses(lhs, method)) {
      return true;
    }
    final IElementType tokenType = expression.getOperationTokenType();
    if (GroovyTokenTypes.mLAND.equals(tokenType) ||
        GroovyTokenTypes.mLOR.equals(tokenType)) {
      return false;
    }
    final GrExpression rhs = expression.getRightOperand();
    return expressionDefinitelyRecurses(rhs, method);
  }

  private static boolean arrayAccessExpressionDefinitelyRecurses(
      GrIndexProperty expression, GrMethod method) {
    final GrExpression arrayExp = expression.getInvokedExpression();
    return expressionDefinitelyRecurses(arrayExp, method);
  }

  private static boolean arrayInitializerExpressionDefinitelyRecurses(
      GrArrayDeclaration expression, GrMethod method) {
    final GrExpression[] initializers = expression.getBoundExpressions();
    for (final GrExpression initializer : initializers) {
      if (expressionDefinitelyRecurses(initializer, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean unaryExpressionDefinitelyRecurses(
      GrUnaryExpression expression, GrMethod method) {
    final GrExpression operand = expression.getOperand();
    return expressionDefinitelyRecurses(operand, method);
  }

  private static boolean instanceOfExpressionDefinitelyRecurses(
      GrInstanceOfExpression expression, GrMethod method) {
    final GrExpression operand = expression.getOperand();
    return expressionDefinitelyRecurses(operand, method);
  }

  private static boolean parenthesizedExpressionDefinitelyRecurses(
      GrParenthesizedExpression expression, GrMethod method) {
    final GrExpression innerExpression = expression.getOperand();
    return expressionDefinitelyRecurses(innerExpression, method);
  }

  private static boolean referenceExpressionDefinitelyRecurses(
      GrReferenceExpression expression, GrMethod method) {

    final GrExpression qualifierExpression =
        expression.getQualifierExpression();
    if (qualifierExpression != null) {
      return expressionDefinitelyRecurses(qualifierExpression, method);
    }
    return false;
  }

  private static boolean typeCastExpressionDefinitelyRecurses(
      GrTypeCastExpression expression, GrMethod method) {
    final GrExpression operand = expression.getOperand();
    return expressionDefinitelyRecurses(operand, method);
  }

  private static boolean assignmentExpressionDefinitelyRecurses(
      GrAssignmentExpression assignmentExpression, GrMethod method) {
    final GrExpression rhs = assignmentExpression.getRValue();
    final GrExpression lhs = assignmentExpression.getLValue();
    return expressionDefinitelyRecurses(rhs, method) ||
        expressionDefinitelyRecurses(lhs, method);
  }

  private static boolean callExpressionDefinitelyRecurses(GrCallExpression exp,
                                                         GrMethod method) {
    final GrArgumentList argumentList = exp.getArgumentList();
    if (argumentList != null) {
      final GrExpression[] args = argumentList.getExpressionArguments();
      for (final GrExpression arg : args) {
        if (expressionDefinitelyRecurses(arg, method)) {
          return true;
        }
      }
      final GrNamedArgument[] namedArgs = argumentList.getNamedArguments();
      for (final GrNamedArgument arg : namedArgs) {
        if (expressionDefinitelyRecurses(arg.getExpression(), method)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean methodCallExpressionDefinitelyRecurses(
      GrMethodCallExpression exp, GrMethod method) {
    final GrExpression invoked = exp.getInvokedExpression();
    if (invoked instanceof GrReferenceExpression) {
      final GrReferenceExpression methodExpression = (GrReferenceExpression) invoked;
      final PsiMethod referencedMethod = exp.resolveMethod();
      if (referencedMethod == null) {
        return false;
      }
      final GrExpression qualifier =
          methodExpression.getQualifierExpression();
      if (referencedMethod.equals(method)) {
        if (method.hasModifierProperty(PsiModifier.STATIC) ||
            method.hasModifierProperty(PsiModifier.PRIVATE)) {
          return true;
        }
        if (qualifier == null || qualifier instanceof GrReferenceExpression && PsiUtil.isThisReference(qualifier)) {
          return true;
        }
      }
      if (expressionDefinitelyRecurses(qualifier, method)) {
        return true;
      }
    }
    return callExpressionDefinitelyRecurses(exp, method);
  }

  private static boolean statementDefinitelyRecurses(@Nullable GrStatement statement,
                                                     GrMethod method) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof GrBreakStatement ||
        statement instanceof GrContinueStatement ||
        statement instanceof GrThrowStatement ||
        statement instanceof GrAssertStatement) {
      return false;
    } else if (statement instanceof GrExpression) {
      final GrExpression expression =
          (GrExpression) statement;
      return expressionDefinitelyRecurses(expression, method);
    } else if (statement instanceof GrVariableDeclaration) {
      final GrVariableDeclaration declaration =
          (GrVariableDeclaration) statement;
      final GrVariable[] declaredElements =
          declaration.getVariables();
      for (final GrVariable variable : declaredElements) {
        final GrExpression initializer = (GrExpression) variable.getInitializer();
        if (expressionDefinitelyRecurses(initializer, method)) {
          return true;
        }
      }
      return false;
    } else if (statement instanceof GrReturnStatement) {
      final GrReturnStatement returnStatement =
          (GrReturnStatement) statement;
      final GrExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        if (expressionDefinitelyRecurses(returnValue, method)) {
          return true;
        }
      }
      return false;
    } else if (statement instanceof GrForStatement) {
      return forStatementDefinitelyRecurses((GrForStatement)
          statement, method);
    } else if (statement instanceof GrWhileStatement) {
      return whileStatementDefinitelyRecurses(
          (GrWhileStatement) statement, method);
    } else if (statement instanceof GrSynchronizedStatement) {
      final GrCodeBlock body = ((GrSynchronizedStatement) statement)
          .getBody();
      return codeBlockDefinitelyRecurses(body, method);
    } else if (statement instanceof GrBlockStatement) {
      final GrCodeBlock codeBlock = ((GrBlockStatement) statement).getBlock();
      return codeBlockDefinitelyRecurses(codeBlock, method);
    } else if (statement instanceof GrIfStatement) {
      return ifStatementDefinitelyRecurses(
          (GrIfStatement) statement, method);
    } else if (statement instanceof GrTryCatchStatement) {
      return tryStatementDefinitelyRecurses(
          (GrTryCatchStatement) statement, method);
    } else if (statement instanceof GrSwitchStatement) {
      return switchStatementDefinitelyRecurses(
          (GrSwitchStatement) statement, method);
    } else {
      // unknown statement type
      return false;
    }
  }

  private static boolean switchStatementDefinitelyRecurses(GrSwitchStatement switchStatement, GrMethod method) {
    final GrExpression switchExpression = switchStatement.getCondition();
    return expressionDefinitelyRecurses(switchExpression, method);
  }

  private static boolean tryStatementDefinitelyRecurses(
      GrTryCatchStatement tryStatement, GrMethod method) {
    final GrCodeBlock tryBlock = tryStatement.getTryBlock();
    if (codeBlockDefinitelyRecurses(tryBlock, method)) {
      return true;
    }
    final GrFinallyClause finallyBlock = tryStatement.getFinallyClause();
    if (finallyBlock == null) {
      return false;
    }
    return codeBlockDefinitelyRecurses(finallyBlock.getBody(), method);
  }

  private static boolean codeBlockDefinitelyRecurses(GrCodeBlock block,
                                                     GrMethod method) {
    if (block == null) {
      return false;
    }
    final GrStatement[] statements = block.getStatements();
    for (final GrStatement statement : statements) {
      if (statementDefinitelyRecurses(statement, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ifStatementDefinitelyRecurses(
      GrIfStatement ifStatement, GrMethod method) {
    final GrExpression condition = ifStatement.getCondition();
    if (condition == null) return false;

    if (expressionDefinitelyRecurses(condition, method)) {
      return true;
    }
    final GrStatement thenBranch = ifStatement.getThenBranch();
    final GrStatement elseBranch = ifStatement.getElseBranch();
    if (thenBranch == null || elseBranch == null) {
      return false;
    }
    return statementDefinitelyRecurses(thenBranch, method) &&
        statementDefinitelyRecurses(elseBranch, method);
  }

  private static boolean forStatementDefinitelyRecurses(GrForStatement forStatement, GrMethod method) {
    final GrForClause clause = forStatement.getClause();
    if (clause == null) return false;
    final GrVariable var = clause.getDeclaredVariable();
    if (var != null) {
      final GrExpression initializer = var.getInitializerGroovy();
      if (expressionDefinitelyRecurses(initializer, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean whileStatementDefinitelyRecurses(
      GrWhileStatement whileStatement, GrMethod method) {

    final GrExpression condition = whileStatement.getCondition();
    if (expressionDefinitelyRecurses(condition, method)) {
      return true;
    }
    if (BoolUtils.isTrue(condition)) {
      final GrStatement body = whileStatement.getBody();
      return statementDefinitelyRecurses(body, method);
    }
    return false;
  }

  public static boolean methodDefinitelyRecurses(
      @NotNull GrMethod method) {
    final GrCodeBlock body = method.getBlock();
    if (body == null) {
      return false;
    }
    return !codeBlockMayReturnBeforeRecursing(body, method, true);
  }
}
