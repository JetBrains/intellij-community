package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InitializationReadUtils {

    private final Set uninitializedReads; // Set to prevent duplicates

    public InitializationReadUtils() {
        super();
        uninitializedReads = new HashSet();
    }

    public List getUninitializedReads() {
        return new ArrayList(uninitializedReads);
    }

    public boolean blockMustAssignVariable(PsiVariable field,
                                           PsiCodeBlock block) {
        return cachingBlockMustAssignVariable(field, block, new HashSet());
    }

    private boolean cachingBlockMustAssignVariable(PsiVariable field, PsiCodeBlock block, Set checkedMethods) {
        if (block == null) {
            return false;
        }

        final PsiStatement[] statements = block.getStatements();
        for (int i = 0; i < statements.length; i++) {
            final PsiStatement statement = statements[i];
            if (statementMustAssignVariable(field, statement, checkedMethods)) {
                return true;
            }
        }
        return false;
    }

    private boolean statementMustAssignVariable(PsiVariable field,
                                                PsiStatement statement,
                                                Set checkedMethods) {
        if (statement == null) {
            return false;
        }
        if (statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiAssertStatement ||
                statement instanceof PsiEmptyStatement) {
            return false;
        } else if (statement instanceof PsiReturnStatement) {
            final PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            return expressionMustAssignVariable(field, returnValue, checkedMethods);
        } else if (statement instanceof PsiThrowStatement) {
            final PsiThrowStatement throwStatement = (PsiThrowStatement) statement;
            final PsiExpression exception = throwStatement.getException();
            return expressionMustAssignVariable(field, exception, checkedMethods);
        } else if (statement instanceof PsiExpressionListStatement) {
            final PsiExpressionListStatement list = (PsiExpressionListStatement) statement;
            final PsiExpressionList expressionList = list.getExpressionList();
            final PsiExpression[] expressions = expressionList.getExpressions();
            for (int i = 0; i < expressions.length; i++) {
                final PsiExpression expression = expressions[i];
                if (expressionMustAssignVariable(field, expression, checkedMethods)) {
                    return true;
                }
            }
            return false;
        } else if (statement instanceof PsiExpressionStatement) {
            final PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
            final PsiExpression expression = expressionStatement.getExpression();
            return expressionMustAssignVariable(field, expression, checkedMethods);
        } else if (statement instanceof PsiDeclarationStatement) {
            return declarationStatementMustAssignVariable(field,
                    (PsiDeclarationStatement) statement,
                    checkedMethods);
        } else if (statement instanceof PsiForStatement) {
            return forStatementMustAssignVariable(field,
                    (PsiForStatement) statement,
                    checkedMethods);
        } else if (statement instanceof PsiForeachStatement) {
            return foreachStatementMustAssignVariable(field,
                    (PsiForeachStatement) statement);
        } else if (statement instanceof PsiWhileStatement) {
            return whileStatementMustAssignVariable(field,
                    (PsiWhileStatement) statement,
                    checkedMethods);
        } else if (statement instanceof PsiDoWhileStatement) {
            return doWhileMustAssignVariable(field,
                    (PsiDoWhileStatement) statement,
                    checkedMethods);
        } else if (statement instanceof PsiSynchronizedStatement) {
            final PsiCodeBlock body = ((PsiSynchronizedStatement) statement).getBody();
            return cachingBlockMustAssignVariable(field, body, checkedMethods);
        } else if (statement instanceof PsiBlockStatement) {
            final PsiCodeBlock codeBlock = ((PsiBlockStatement) statement).getCodeBlock();
            return cachingBlockMustAssignVariable(field, codeBlock, checkedMethods);
        } else if (statement instanceof PsiLabeledStatement) {
            final PsiLabeledStatement labeledStatement = (PsiLabeledStatement) statement;
            final PsiStatement statementLabeled = labeledStatement.getStatement();
            return statementMustAssignVariable(field, statementLabeled, checkedMethods);
        } else if (statement instanceof PsiIfStatement) {
            return ifStatementMustAssignVariable(field,
                    (PsiIfStatement) statement,
                    checkedMethods);
        } else if (statement instanceof PsiTryStatement) {
            return tryStatementMustAssignVariable(field,
                    (PsiTryStatement) statement,
                    checkedMethods);
        } else if (statement instanceof PsiSwitchStatement) {
            return false;
        } else   // unknown statement type
        {
            return false;
        }
    }

    private boolean declarationStatementMustAssignVariable(PsiVariable field,
                                                           PsiDeclarationStatement declarationStatement,
                                                           Set checkedMethods) {
        final PsiElement[] elements = declarationStatement.getDeclaredElements();
        for (int i = 0; i < elements.length; i++) {
          if (elements[i] instanceof PsiVariable) {
            final PsiVariable variable = (PsiVariable) elements[i];
            final PsiExpression initializer = variable.getInitializer();
            if (expressionMustAssignVariable(field, initializer, checkedMethods)) {
                return true;
            }
          }
        }
        return false;
    }

    private boolean tryStatementMustAssignVariable(PsiVariable field,
                                                   PsiTryStatement tryStatement,
                                                   Set checkedMethods) {
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        if (catchBlocks == null || catchBlocks.length == 0) {
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (cachingBlockMustAssignVariable(field, tryBlock, checkedMethods)) {
                return true;
            }
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        return cachingBlockMustAssignVariable(field, finallyBlock, checkedMethods);
    }

    private boolean ifStatementMustAssignVariable(PsiVariable field,
                                                  PsiIfStatement ifStatement,
                                                  Set checkedMethods) {
        final PsiExpression condition = ifStatement.getCondition();
        if (expressionMustAssignVariable(field, condition, checkedMethods)) {
            return true;
        }
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        return statementMustAssignVariable(field, thenBranch, checkedMethods) &&
                statementMustAssignVariable(field, elseBranch, checkedMethods);
    }

    private boolean doWhileMustAssignVariable(PsiVariable field,
                                              PsiDoWhileStatement doWhileStatement,
                                              Set checkedMethods) {
        final PsiExpression condition = doWhileStatement.getCondition();
        final PsiStatement body = doWhileStatement.getBody();
        return expressionMustAssignVariable(field, condition, checkedMethods) ||
                statementMustAssignVariable(field, body, checkedMethods);
    }

    private boolean whileStatementMustAssignVariable(PsiVariable field,
                                                     PsiWhileStatement whileStatement,
                                                     Set checkedMethods) {
        final PsiExpression condition = whileStatement.getCondition();
        if (expressionMustAssignVariable(field, condition, checkedMethods)) {
            return true;
        }
        if (BoolUtils.isTrue(condition)) {
            final PsiStatement body = whileStatement.getBody();
            if (statementMustAssignVariable(field, body, checkedMethods)) {
                return true;
            }
        }
        return false;
    }

    private boolean forStatementMustAssignVariable(PsiVariable field,
                                                   PsiForStatement forStatement,
                                                   Set checkedMethods) {
        final PsiStatement initialization = forStatement.getInitialization();
        if (statementMustAssignVariable(field, initialization, checkedMethods)) {
            return true;
        }
        final PsiExpression test = forStatement.getCondition();
        if (expressionMustAssignVariable(field, test, checkedMethods)) {
            return true;
        }
        if (BoolUtils.isTrue(test)) {
            final PsiStatement body = forStatement.getBody();
            if (statementMustAssignVariable(field, body, checkedMethods)) {
                return true;
            }
            final PsiStatement update = forStatement.getUpdate();
            if (statementMustAssignVariable(field, update, checkedMethods)) {
                return true;
            }
        }
        return false;
    }

    private boolean foreachStatementMustAssignVariable(PsiVariable field,
                                                       PsiForeachStatement forStatement) {
        return false;
    }

    private boolean expressionMustAssignVariable(PsiVariable field,
                                                 PsiExpression expression,
                                                 Set checkedMethods) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof PsiThisExpression ||
                expression instanceof PsiLiteralExpression ||
                expression instanceof PsiSuperExpression ||
                expression instanceof PsiClassObjectAccessExpression) {
            return false;
        } else if (expression instanceof PsiReferenceExpression) {

            final PsiReferenceExpression refExp = (PsiReferenceExpression) expression;
            if (field.equals(refExp.resolve())) {

                if (refExp.getParent() instanceof PsiAssignmentExpression) {
                    final PsiAssignmentExpression pae = (PsiAssignmentExpression) refExp.getParent();
                    if (pae.getRExpression() != null) {
                        if (pae.getRExpression().equals(refExp)) {
                            if (!refExp.isQualified() || refExp.getQualifierExpression() instanceof PsiThisExpression) {
                                uninitializedReads.add(expression);
                            }
                        }
                    }
                } else {
                    if (!refExp.isQualified() || refExp.getQualifierExpression() instanceof PsiThisExpression) {
                        uninitializedReads.add(expression);
                    }
                }
            }
            if (refExp.isQualified()) {
                return expressionMustAssignVariable(field, refExp.getQualifierExpression(), checkedMethods);
            } else {
                return false;
            }
        } else if (expression instanceof PsiMethodCallExpression) {
            final PsiMethod method = (PsiMethod) PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            if (method != null) {
                final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) expression).getMethodExpression();
                if (methodExpression != null) {
                    final PsiMethod calledMethod = (PsiMethod) methodExpression.resolve();
                    if (method.equals(calledMethod)) {
                        // Skip recursive call to self that causes StackOverflowError.
                        return false;
                    }
                }
            }
            return methodCallMustAssignVariable(expression, field, checkedMethods);
        } else if (expression instanceof PsiNewExpression) {
            return newExpressionMustAssignVariable(expression, field, checkedMethods);
        } else if (expression instanceof PsiArrayInitializerExpression) {
            final PsiArrayInitializerExpression array =
                    (PsiArrayInitializerExpression) expression;
            final PsiExpression[] initializers = array.getInitializers();
            for (int i = 0; i < initializers.length; i++) {
                final PsiExpression initializer = initializers[i];
                if (expressionMustAssignVariable(field, initializer, checkedMethods)) {
                    return true;
                }
            }
            return false;
        } else if (expression instanceof PsiTypeCastExpression) {
            final PsiTypeCastExpression typeCast = (PsiTypeCastExpression) expression;
            final PsiExpression operand = typeCast.getOperand();
            return expressionMustAssignVariable(field, operand, checkedMethods);
        } else if (expression instanceof PsiArrayAccessExpression) {
            final PsiArrayAccessExpression accessExpression = (PsiArrayAccessExpression) expression;
            final PsiExpression arrayExpression = accessExpression.getArrayExpression();
            final PsiExpression indexExpression = accessExpression.getIndexExpression();
            return expressionMustAssignVariable(field, arrayExpression, checkedMethods) ||
                    expressionMustAssignVariable(field, indexExpression, checkedMethods);
        } else if (expression instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
            final PsiExpression operand = prefixExpression.getOperand();
            return expressionMustAssignVariable(field, operand, checkedMethods);
        } else if (expression instanceof PsiPostfixExpression) {
            final PsiPostfixExpression postfixExpression = (PsiPostfixExpression) expression;
            final PsiExpression operand = postfixExpression.getOperand();
            return expressionMustAssignVariable(field, operand, checkedMethods);
        } else if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            return expressionMustAssignVariable(field, lhs, checkedMethods) ||
                    expressionMustAssignVariable(field, rhs, checkedMethods);
        } else if (expression instanceof PsiConditionalExpression) {
            final PsiConditionalExpression conditional = (PsiConditionalExpression) expression;
            final PsiExpression condition = conditional.getCondition();
            if (expressionMustAssignVariable(field, condition, checkedMethods)) {
                return true;
            }
            final PsiExpression thenExpression = conditional.getThenExpression();
            final PsiExpression elseExpression = conditional.getElseExpression();
            return expressionMustAssignVariable(field, thenExpression, checkedMethods) &&
                    expressionMustAssignVariable(field, elseExpression, checkedMethods);
        } else if (expression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignment = (PsiAssignmentExpression) expression;
            final PsiExpression lhs = assignment.getLExpression();
            if (expressionMustAssignVariable(field, lhs, checkedMethods)) {
                return true;
            }
            final PsiExpression rhs = assignment.getRExpression();
            if (expressionMustAssignVariable(field, rhs, checkedMethods)) {
                return true;
            }
            if (lhs instanceof PsiReferenceExpression) {
                final PsiElement element = ((PsiReferenceExpression) lhs).resolve();
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

    private boolean newExpressionMustAssignVariable(PsiExpression expression,
                                                    PsiVariable field,
                                                    Set checkedMethods) {
        final PsiNewExpression callExpression =
                (PsiNewExpression) expression;
        final PsiExpressionList argumentList = callExpression.getArgumentList();
        if (argumentList != null) {
            final PsiExpression[] args = argumentList.getExpressions();
            for (int i = 0; i < args.length; i++) {
                final PsiExpression arg = args[i];
                if (expressionMustAssignVariable(field, arg, checkedMethods)) {
                    return true;
                }
            }
        }
        final PsiArrayInitializerExpression arrayInitializer = callExpression.getArrayInitializer();
        if (expressionMustAssignVariable(field, arrayInitializer, checkedMethods)) {
            return true;
        }
        final PsiExpression[] arrayDimensions = callExpression.getArrayDimensions();
        if (arrayDimensions != null) {
            for (int i = 0; i < arrayDimensions.length; i++) {
                final PsiExpression dim = arrayDimensions[i];
                if (expressionMustAssignVariable(field, dim, checkedMethods)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean methodCallMustAssignVariable(PsiExpression expression,
                                                 PsiVariable field, Set checkedMethods) {
        final PsiMethodCallExpression callExpression = (PsiMethodCallExpression) expression;

        final PsiExpressionList argList = callExpression.getArgumentList();
        final PsiExpression[] args = argList.getExpressions();
        for (int i = 0; i < args.length; i++) {
            final PsiExpression arg = args[i];
            if (expressionMustAssignVariable(field, arg, checkedMethods)) {
                return true;
            }
        }
        final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
        if (expressionMustAssignVariable(field, methodExpression, checkedMethods)) {
            return true;
        }
        final PsiMethod method = callExpression.resolveMethod();
        if (method == null) {
            return false;
        }
        final MethodSignature methodSignature =
                MethodSignatureUtil.createMethodSignature(method.getName(),
                        method.getParameterList(),
                        method.getTypeParameterList(),
                        EmptySubstitutor.getInstance());
        if (!checkedMethods.add(methodSignature)) {
            return false;
        }

        final PsiClass containingClass =
                ClassUtils.getContainingClass(expression);
        final PsiClass calledClass = method.getContainingClass();

        // Can remark out this block to continue chase outside of of current class

        if (!calledClass.equals(containingClass)) {
            return false;
        }

        if (method.hasModifierProperty(PsiModifier.STATIC)
                || method.isConstructor()
                || method.hasModifierProperty(PsiModifier.PRIVATE)
                || method.hasModifierProperty(PsiModifier.FINAL)
                || calledClass.hasModifierProperty(PsiModifier.FINAL)) {
            final PsiCodeBlock body = method.getBody();
            return cachingBlockMustAssignVariable(field, body, checkedMethods);
        }
        return false;
    }

}
