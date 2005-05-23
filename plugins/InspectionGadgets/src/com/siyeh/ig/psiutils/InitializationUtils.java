package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class InitializationUtils {
    private InitializationUtils() {
        super();
    }

    public static boolean blockMustAssignVariableOrFail(@NotNull PsiVariable field,
                                                        @Nullable PsiCodeBlock block) {
        return cachingblockMustAssignVariableOrFail(field, block, new HashSet<MethodSignature>());
    }

    private static boolean cachingblockMustAssignVariableOrFail(@NotNull PsiVariable field,
                                                                @Nullable PsiCodeBlock block,
                                                                @NotNull Set<MethodSignature> checkedMethods) {
        if (block == null) {
            return false;
        }
        final PsiStatement[] statements = block.getStatements();
        for(final PsiStatement statement : statements){
            if(statementMustAssignVariableOrFail(field, statement,
                                                 checkedMethods)){
                return true;
            }
        }
        return false;
    }


    private static boolean statementMustAssignVariableOrFail(@NotNull PsiVariable field, PsiStatement statement, Set<MethodSignature> checkedMethods) {
        if (statement == null) {
            return false;
        }
        if(statementMustThrowException(statement))
        {
            return true;
        }
        if (statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiAssertStatement ||
                statement instanceof PsiEmptyStatement) {
            return false;
        } else if (statement instanceof PsiReturnStatement) {
            final PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            return expressionMustAssignVariableOrFail(field, returnValue, checkedMethods);
        } else if (statement instanceof PsiThrowStatement) {
            final PsiThrowStatement throwStatement = (PsiThrowStatement) statement;
            final PsiExpression exception = throwStatement.getException();
            return expressionMustAssignVariableOrFail(field, exception, checkedMethods);
        } else if (statement instanceof PsiExpressionListStatement) {
            final PsiExpressionListStatement list = (PsiExpressionListStatement) statement;
            final PsiExpressionList expressionList = list.getExpressionList();
            final PsiExpression[] expressions = expressionList.getExpressions();
            for(final PsiExpression expression : expressions){
                if(expressionMustAssignVariableOrFail(field, expression,
                                                      checkedMethods)){
                    return true;
                }
            }
            return false;
        } else if (statement instanceof PsiExpressionStatement) {
            final PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
            final PsiExpression expression = expressionStatement.getExpression();
            return expressionMustAssignVariableOrFail(field, expression, checkedMethods);
        } else if (statement instanceof PsiDeclarationStatement) {
            return declarationStatementMustAssignVariableOrFail(field, (PsiDeclarationStatement) statement, checkedMethods);
        } else if (statement instanceof PsiForStatement) {
            return forStatementMustAssignVariableOrFail(field, (PsiForStatement) statement, checkedMethods);
        } else if (statement instanceof PsiForeachStatement) {
            return foreachStatementMustAssignVariableOrFail(field, (PsiForeachStatement) statement);
        } else if (statement instanceof PsiWhileStatement) {
            return whileStatementMustAssignVariableOrFail(field, (PsiWhileStatement) statement, checkedMethods);
        } else if (statement instanceof PsiDoWhileStatement) {
            return doWhileMustAssignVariableOrFail(field, (PsiDoWhileStatement) statement, checkedMethods);
        } else if (statement instanceof PsiSynchronizedStatement) {
            final PsiCodeBlock body = ((PsiSynchronizedStatement) statement).getBody();
            return cachingblockMustAssignVariableOrFail(field, body, checkedMethods);
        } else if (statement instanceof PsiBlockStatement) {
            final PsiCodeBlock codeBlock = ((PsiBlockStatement) statement).getCodeBlock();
            return cachingblockMustAssignVariableOrFail(field, codeBlock, checkedMethods);
        } else if (statement instanceof PsiLabeledStatement) {
            final PsiLabeledStatement labeledStatement = (PsiLabeledStatement) statement;
            final PsiStatement statementLabeled = labeledStatement.getStatement();
            return statementMustAssignVariableOrFail(field, statementLabeled, checkedMethods);
        } else if (statement instanceof PsiIfStatement) {
            return ifStatementMustAssignVariableOrFail(field, (PsiIfStatement) statement, checkedMethods);
        } else if (statement instanceof PsiTryStatement) {
            return tryStatementMustAssignVariableOrFail(field, (PsiTryStatement) statement, checkedMethods);
        } else if (statement instanceof PsiSwitchStatement) {
            return false;
        } else   // unknown statement type
        {
            return false;
        }
    }

    private static boolean declarationStatementMustAssignVariableOrFail(PsiVariable field,
                                                                  PsiDeclarationStatement declarationStatement,
                                                                  Set<MethodSignature> checkedMethods) {
        final PsiElement[] elements = declarationStatement.getDeclaredElements();
        for(PsiElement element : elements){
            final PsiVariable variable = (PsiVariable) element;
            final PsiExpression initializer = variable.getInitializer();
            if(expressionMustAssignVariableOrFail(field, initializer,
                                                  checkedMethods)){
                return true;
            }
        }
        return false;
    }

    private static boolean tryStatementMustAssignVariableOrFail(PsiVariable field, PsiTryStatement tryStatement, Set<MethodSignature> checkedMethods) {
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        if (catchBlocks == null || catchBlocks.length == 0) {
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (cachingblockMustAssignVariableOrFail(field, tryBlock, checkedMethods)) {
                return true;
            }
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        return cachingblockMustAssignVariableOrFail(field, finallyBlock, checkedMethods);
    }

    private static boolean ifStatementMustAssignVariableOrFail(PsiVariable field, PsiIfStatement ifStatement, Set<MethodSignature> checkedMethods) {
        final PsiExpression condition = ifStatement.getCondition();
        if (expressionMustAssignVariableOrFail(field, condition, checkedMethods)) {
            return true;
        }
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        return statementMustAssignVariableOrFail(field, thenBranch, checkedMethods) &&
                statementMustAssignVariableOrFail(field, elseBranch, checkedMethods);
    }

    private static boolean doWhileMustAssignVariableOrFail(PsiVariable field, PsiDoWhileStatement doWhileStatement, Set<MethodSignature> checkedMethods) {
        final PsiExpression condition = doWhileStatement.getCondition();
        final PsiStatement body = doWhileStatement.getBody();
        return expressionMustAssignVariableOrFail(field, condition, checkedMethods) ||
                statementMustAssignVariableOrFail(field, body, checkedMethods);
    }

    private static boolean whileStatementMustAssignVariableOrFail(PsiVariable field, PsiWhileStatement whileStatement, Set<MethodSignature> checkedMethods) {
        final PsiExpression condition = whileStatement.getCondition();
        if (expressionMustAssignVariableOrFail(field, condition, checkedMethods)) {
            return true;
        }
        if (BoolUtils.isTrue(condition)) {
            final PsiStatement body = whileStatement.getBody();
            if (statementMustAssignVariableOrFail(field, body, checkedMethods)) {
                return true;
            }
        }
        return false;
    }

    private static boolean forStatementMustAssignVariableOrFail(PsiVariable field, PsiForStatement forStatement, Set<MethodSignature> checkedMethods) {
        final PsiStatement initialization = forStatement.getInitialization();
        if (statementMustAssignVariableOrFail(field, initialization, checkedMethods)) {
            return true;
        }
        final PsiExpression test = forStatement.getCondition();
        if (expressionMustAssignVariableOrFail(field, test, checkedMethods)) {
            return true;
        }
        if (BoolUtils.isTrue(test)) {
            final PsiStatement body = forStatement.getBody();
            if (statementMustAssignVariableOrFail(field, body, checkedMethods)) {
                return true;
            }
            final PsiStatement update = forStatement.getUpdate();
            if (statementMustAssignVariableOrFail(field, update, checkedMethods)) {
                return true;
            }
        }
        return false;
    }

    private static boolean foreachStatementMustAssignVariableOrFail(PsiVariable field, PsiForeachStatement forStatement) {
        return false;
    }

    private static boolean expressionMustAssignVariableOrFail(PsiVariable field, PsiExpression expression, Set<MethodSignature> checkedMethods) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof PsiThisExpression ||
                expression instanceof PsiLiteralExpression ||
                expression instanceof PsiSuperExpression ||
                expression instanceof PsiClassObjectAccessExpression) {
            return false;
        } else if (expression instanceof PsiReferenceExpression) {
            return false;
        } else if (expression instanceof PsiMethodCallExpression) {
            return methodCallMustAssignVariableOrFail(expression, field, checkedMethods);
        } else if (expression instanceof PsiNewExpression) {
            return newExpressionMustAssignVariableOrFail(expression, field, checkedMethods);
        } else if (expression instanceof PsiArrayInitializerExpression) {
            final PsiArrayInitializerExpression array =
                    (PsiArrayInitializerExpression) expression;
            final PsiExpression[] initializers = array.getInitializers();
            for(final PsiExpression initializer : initializers){
                if(expressionMustAssignVariableOrFail(field, initializer,
                                                      checkedMethods)){
                    return true;
                }
            }
            return false;
        } else if (expression instanceof PsiTypeCastExpression) {
            final PsiTypeCastExpression typeCast = (PsiTypeCastExpression) expression;
            final PsiExpression operand = typeCast.getOperand();
            return expressionMustAssignVariableOrFail(field, operand, checkedMethods);
        } else if (expression instanceof PsiArrayAccessExpression) {
            final PsiArrayAccessExpression accessExpression = (PsiArrayAccessExpression) expression;
            final PsiExpression arrayExpression = accessExpression.getArrayExpression();
            final PsiExpression indexExpression = accessExpression.getIndexExpression();
            return expressionMustAssignVariableOrFail(field, arrayExpression, checkedMethods) ||
                    expressionMustAssignVariableOrFail(field, indexExpression, checkedMethods);
        } else if (expression instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
            final PsiExpression operand = prefixExpression.getOperand();
            return expressionMustAssignVariableOrFail(field, operand, checkedMethods);
        } else if (expression instanceof PsiPostfixExpression) {
            final PsiPostfixExpression postfixExpression = (PsiPostfixExpression) expression;
            final PsiExpression operand = postfixExpression.getOperand();
            return expressionMustAssignVariableOrFail(field, operand, checkedMethods);
        } else if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            return expressionMustAssignVariableOrFail(field, lhs, checkedMethods) ||
                    expressionMustAssignVariableOrFail(field, rhs, checkedMethods);
        } else if (expression instanceof PsiConditionalExpression) {
            final PsiConditionalExpression conditional = (PsiConditionalExpression) expression;
            final PsiExpression condition = conditional.getCondition();
            if (expressionMustAssignVariableOrFail(field, condition, checkedMethods)) {
                return true;
            }
            final PsiExpression thenExpression = conditional.getThenExpression();
            final PsiExpression elseExpression = conditional.getElseExpression();
            return expressionMustAssignVariableOrFail(field, thenExpression, checkedMethods) &&
                    expressionMustAssignVariableOrFail(field, elseExpression, checkedMethods);
        } else if (expression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignment = (PsiAssignmentExpression) expression;
            final PsiExpression lhs = assignment.getLExpression();
            if (expressionMustAssignVariableOrFail(field, lhs, checkedMethods)) {
                return true;
            }
            final PsiExpression rhs = assignment.getRExpression();
            if (expressionMustAssignVariableOrFail(field, rhs, checkedMethods)) {
                return true;
            }
            if (lhs instanceof PsiReferenceExpression) {
                final PsiElement element = ((PsiReference) lhs).resolve();
                if (element != null &&
                        field != null &&
                        element.equals(field)) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    private static boolean newExpressionMustAssignVariableOrFail(PsiExpression expression, PsiVariable field, Set<MethodSignature> checkedMethods) {
        final PsiNewExpression callExpression =
                (PsiNewExpression) expression;
        final PsiExpressionList argumentList = callExpression.getArgumentList();
        if (argumentList != null) {
            final PsiExpression[] args = argumentList.getExpressions();
            for(final PsiExpression arg : args){
                if(expressionMustAssignVariableOrFail(field, arg,
                                                      checkedMethods)){
                    return true;
                }
            }
        }
        final PsiArrayInitializerExpression arrayInitializer = callExpression.getArrayInitializer();
        if (expressionMustAssignVariableOrFail(field, arrayInitializer, checkedMethods)) {
            return true;
        }
        final PsiExpression[] arrayDimensions = callExpression.getArrayDimensions();
        if (arrayDimensions != null) {
            for(final PsiExpression dim : arrayDimensions){
                if(expressionMustAssignVariableOrFail(field, dim,
                                                      checkedMethods)){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean methodCallMustAssignVariableOrFail(PsiExpression expression, PsiVariable field, Set<MethodSignature> checkedMethods) {
        final PsiMethodCallExpression callExpression =
                (PsiMethodCallExpression) expression;
        final PsiExpressionList argList = callExpression.getArgumentList();
        if(argList == null){
            return false;
        }
        final PsiExpression[] args = argList.getExpressions();
        for(final PsiExpression arg : args){
            if(expressionMustAssignVariableOrFail(field, arg, checkedMethods)){
                return true;
            }
        }
        final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
        if (expressionMustAssignVariableOrFail(field, methodExpression, checkedMethods)) {
            return true;
        }
        final PsiMethod method = callExpression.resolveMethod();
        if (method == null) {
            return false;
        }
        final MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
        if (!checkedMethods.add(methodSignature)) {
            return false;
        }
        final PsiClass containingClass =
                ClassUtils.getContainingClass(expression);
        final PsiClass calledClass = method.getContainingClass();
        if (!calledClass.equals(containingClass)) {
            return false;
        }
        if (method.hasModifierProperty(PsiModifier.STATIC)
                || method.isConstructor()
                || method.hasModifierProperty(PsiModifier.PRIVATE)
                || method.hasModifierProperty(PsiModifier.FINAL)
                || calledClass.hasModifierProperty(PsiModifier.FINAL)) {
            final PsiCodeBlock body = method.getBody();
            return cachingblockMustAssignVariableOrFail(field, body, checkedMethods);
        }
        return false;
    }


    private static boolean statementMustThrowException(  PsiStatement statement) {
        if (statement == null) {
            return false;
        }
        if (statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiAssertStatement ||
                statement instanceof PsiReturnStatement ||
                statement instanceof PsiExpressionStatement ||
                statement instanceof PsiExpressionListStatement ||
                statement instanceof PsiForeachStatement ||
                statement instanceof PsiDeclarationStatement ||
                statement instanceof PsiEmptyStatement) {
            return false;
        }  else if (statement instanceof PsiThrowStatement) {
           return true;
        }  else if (statement instanceof PsiForStatement) {
            return forStatementMustThrowException( (PsiForStatement) statement);
        } else if (statement instanceof PsiWhileStatement) {
            return whileStatementMustThrowException( (PsiWhileStatement) statement);
        } else if (statement instanceof PsiDoWhileStatement) {
            return doWhileMustThrowException( (PsiDoWhileStatement) statement);
        } else if (statement instanceof PsiSynchronizedStatement) {
            final PsiCodeBlock body = ((PsiSynchronizedStatement) statement).getBody();
            return blockMustThrowException( body);
        } else if (statement instanceof PsiBlockStatement) {
            final PsiCodeBlock codeBlock = ((PsiBlockStatement) statement).getCodeBlock();
            return blockMustThrowException( codeBlock);
        } else if (statement instanceof PsiLabeledStatement) {
            final PsiLabeledStatement labeledStatement = (PsiLabeledStatement) statement;
            final PsiStatement statementLabeled = labeledStatement.getStatement();
            return statementMustThrowException( statementLabeled);
        } else if (statement instanceof PsiIfStatement) {
            return ifStatementMustThrowException( (PsiIfStatement) statement);
        } else if (statement instanceof PsiTryStatement) {
            return tryStatementMustThrowException( (PsiTryStatement) statement);
        } else if (statement instanceof PsiSwitchStatement) {
            return false;
        } else   // unknown statement type
        {
            return false;
        }
    }

    private static boolean blockMustThrowException(PsiCodeBlock block) {
        if (block == null) {
            return false;
        }
        final PsiStatement[] statements = block.getStatements();
        for(final PsiStatement statement : statements){
            if(statementMustThrowException(statement)){
                return true;
            }
        }
        return false;
    }
    private static boolean tryStatementMustThrowException( PsiTryStatement tryStatement) {
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        if (catchBlocks == null || catchBlocks.length == 0) {
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (blockMustThrowException( tryBlock)) {
                return true;
            }
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        return blockMustThrowException( finallyBlock);
    }

    private static boolean ifStatementMustThrowException( PsiIfStatement ifStatement) {
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        return statementMustThrowException( thenBranch) &&
                statementMustThrowException( elseBranch);
    }

    private static boolean doWhileMustThrowException( PsiDoWhileStatement doWhileStatement) {
        final PsiStatement body = doWhileStatement.getBody();
        return statementMustThrowException( body);
    }

    private static boolean whileStatementMustThrowException( PsiWhileStatement whileStatement) {
        final PsiExpression condition = whileStatement.getCondition();
        if (BoolUtils.isTrue(condition)) {
            final PsiStatement body = whileStatement.getBody();
            if (statementMustThrowException( body)) {
                return true;
            }
        }
        return false;
    }

    private static boolean forStatementMustThrowException( PsiForStatement forStatement) {
        final PsiStatement initialization = forStatement.getInitialization();
        if (statementMustThrowException( initialization)) {
            return true;
        }
        final PsiExpression test = forStatement.getCondition();
        if (BoolUtils.isTrue(test)) {
            final PsiStatement body = forStatement.getBody();
            if (statementMustThrowException( body)) {
                return true;
            }
            final PsiStatement update = forStatement.getUpdate();
            if (statementMustThrowException( update)) {
                return true;
            }
        }
        return false;
    }

}
