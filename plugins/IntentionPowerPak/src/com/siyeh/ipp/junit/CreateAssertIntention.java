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
package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.AnnotationUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class CreateAssertIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new CreateAssertPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiExpressionStatement statement =
                (PsiExpressionStatement)element;
        assert statement != null;
        final PsiExpression expression = statement.getExpression();
        final PsiMethod containingMethod =
                PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
        final String specifierString;
        if(containingMethod != null &&
                AnnotationUtil.isAnnotated(containingMethod,
                        "org.junit.Test", true)) {
            specifierString = "org.junit.Assert.";
        } else {
            specifierString = "";
        }
        if (BoolUtils.isNegation(expression)) {
            @NonNls final String newExpression =
                    specifierString + "assertFalse(" +
                            BoolUtils.getNegatedExpressionText(expression) + ");";
            replaceStatementAndShorten(newExpression,
                    statement);
        } else if (isNullComparison(expression)) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            final PsiExpression comparedExpression;
            if (isNull(lhs)) {
                comparedExpression = rhs;
            } else {
                comparedExpression = lhs;
            }
            assert comparedExpression != null;
            @NonNls final String newExpression = specifierString +
                    "assertNull(" + comparedExpression.getText() + ");";
            replaceStatementAndShorten(newExpression,
                    statement);
        } else if (isEqualityComparison(expression)) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            final PsiExpression comparedExpression;
            final PsiExpression comparingExpression;
            if (rhs instanceof PsiLiteralExpression) {
                comparedExpression = rhs;
                comparingExpression = lhs;
            } else {
                comparedExpression = lhs;
                comparingExpression = rhs;
            }
            assert comparingExpression != null;
            final PsiType type = lhs.getType();
            @NonNls final String newExpression;
            if (PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type)) {
                newExpression = specifierString + "assertEquals(" +
                        comparedExpression.getText() + ", " +
                        comparingExpression.getText() + ", 0.0);";
            } else if (type instanceof PsiPrimitiveType) {
                newExpression = specifierString + "assertEquals(" +
                        comparedExpression.getText() + ", " +
                        comparingExpression.getText() + ");";
            } else {
                newExpression = specifierString + "assertSame(" +
                        comparedExpression.getText() + ", " +
                        comparingExpression.getText() + ");";
            }
            replaceStatementAndShorten(newExpression,
                    statement);
        } else if (isEqualsExpression(expression)) {
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression)expression;
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final PsiExpression comparedExpression =
                    methodExpression.getQualifierExpression();
            assert comparedExpression != null;
            final PsiExpressionList argList = call.getArgumentList();
            final PsiExpression comparingExpression =
                    argList.getExpressions()[0];
            @NonNls final String newExpression;
            if (comparingExpression instanceof PsiLiteralExpression) {
                newExpression = specifierString + "assertEquals(" +
                        comparingExpression.getText() + ", " +
                        comparedExpression.getText() + ");";
            } else {
                newExpression = specifierString + "assertEquals(" +
                        comparedExpression.getText() + ", " +
                        comparingExpression.getText() + ");";
            }
            replaceStatementAndShorten(newExpression,
                    statement);
        } else {
            @NonNls final String newExpression =
                    specifierString + "assertTrue(" + expression.getText() + ");";
            replaceStatementAndShorten(newExpression,
                    statement);
        }
    }

    private static boolean isEqualsExpression(PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (!"equals".equals(methodName)) {
            return false;
        }
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if (qualifier == null) {
            return false;
        }
        final PsiExpressionList argList = call.getArgumentList();
        final PsiExpression[] expressions = argList.getExpressions();
        return expressions.length == 1 && expressions[0] != null;
    }

    private static boolean isEqualityComparison(PsiExpression expression) {
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        return JavaTokenType.EQEQ.equals(tokenType);
    }

    private static boolean isNullComparison(PsiExpression expression) {
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (!JavaTokenType.EQEQ.equals(tokenType)) {
            return false;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        if (isNull(lhs)) {
            return true;
        }
        final PsiExpression Rhs = binaryExpression.getROperand();
        return isNull(Rhs);
    }

    private static boolean isNull(PsiExpression expression) {
        if (!(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        @NonNls final String text = expression.getText();
        return PsiKeyword.NULL.equals(text);
    }
}
