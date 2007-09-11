/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public class NonNlsUtils {

    private static final Key<Boolean> KEY = new Key("IG_NON_NLS_ANNOTATED_USE");

    private NonNlsUtils() {
    }

    public static boolean isNonNlsAnnotated(
            @Nullable PsiExpression expression) {
        if (isReferenceToNonNlsAnnotatedElement(expression)) {
            return true;
        }
        if (expression instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) expression;
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (isNonNlsAnnotatedModifierListOwner(method)) {
                return true;
            }
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            return isNonNlsAnnotated(qualifier);
        } else if (expression instanceof PsiArrayAccessExpression) {
            final PsiArrayAccessExpression arrayAccessExpression =
                    (PsiArrayAccessExpression)expression;
            final PsiExpression arrayExpression =
                    arrayAccessExpression.getArrayExpression();
            return isNonNlsAnnotated(arrayExpression);
        }
        return false;
    }

    public static boolean isNonNlsAnnotatedUse(
            @Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final Boolean value = getCachedValue(expression, KEY);
        if (value != null) {
            return value.booleanValue();
        }
        final PsiElement element =
                PsiTreeUtil.getParentOfType(expression,
                        PsiExpressionList.class,
                        PsiAssignmentExpression.class,
                        PsiVariable.class,
                        PsiReturnStatement.class);
        final boolean result;
        if (element instanceof PsiExpressionList) {
            final PsiExpressionList expressionList =
                    (PsiExpressionList) element;
            result = isNonNlsAnnotatedParameter(expression, expressionList);
        } else if (element instanceof PsiVariable) {
            result = isNonNlsAnnotatedModifierListOwner(element);
        } else if (element instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) element;
            result =
                    isAssignmentToNonNlsAnnotatedVariable(assignmentExpression);
        } else if (element instanceof PsiReturnStatement) {
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            result = isNonNlsAnnotatedModifierListOwner(method);
        } else {
            result = false;
        }
        putCachedValue(expression, KEY, Boolean.valueOf(result));
        return result;
    }

    private static <T> void putCachedValue(PsiExpression expression,
                                       Key<T> key, T value) {
        if (expression instanceof PsiBinaryExpression) {
            expression.putUserData(key, value);
        }
    }

    @Nullable
    private static <T> T getCachedValue(PsiExpression expression, Key<T> key) {
        final T data = expression.getUserData(key);
        if (!(expression instanceof PsiBinaryExpression)) {
            return data;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)expression;
        final PsiExpression lhs = binaryExpression.getLOperand();
        T childData = null;
        if (lhs instanceof PsiBinaryExpression ){
            childData = lhs.getUserData(key);
        }
        if (childData == null) {
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs instanceof PsiBinaryExpression) {
                childData = rhs.getUserData(key);
            }
        }
        if (childData != data) {
            expression.putUserData(key, childData);
        }
        return childData;
    }

    private static boolean isAssignmentToNonNlsAnnotatedVariable(
            PsiAssignmentExpression assignmentExpression) {
        final PsiExpression lhs = assignmentExpression.getLExpression();
        return isReferenceToNonNlsAnnotatedElement(lhs);
    }

    private static boolean isReferenceToNonNlsAnnotatedElement(
            @Nullable PsiExpression expression) {
        if (!(expression instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression)expression;
        final PsiElement target = referenceExpression.resolve();
        return isNonNlsAnnotatedModifierListOwner(target);
    }

    private static boolean isNonNlsAnnotatedParameter(
            PsiExpression expression,
            PsiExpressionList expressionList) {
        final PsiElement parent = expressionList.getParent();
        if (!(parent instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) parent;
        final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if (isReferenceToNonNlsAnnotatedElement(qualifier)) {
            return true;
        }
        final PsiExpression[] expressions = expressionList.getExpressions();
        int index = -1;
        for (int i = 0; i < expressions.length; i++) {
            final PsiExpression argument = expressions[i];
            if (PsiTreeUtil.isAncestor(argument, expression, false)) {
                index = i;
            }
        }
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter parameter;
        if (index < parameters.length) {
            parameter = parameters[index];
        } else {
            parameter = parameters[parameters.length - 1];
        }
        return isNonNlsAnnotatedModifierListOwner(parameter);
    }

    private static boolean isNonNlsAnnotatedModifierListOwner(
            @Nullable PsiElement element) {
        if (!(element instanceof PsiModifierListOwner)) {
            return false;
        }
        final PsiModifierListOwner variable = (PsiModifierListOwner) element;
        return AnnotationUtil.isAnnotated(variable,
                AnnotationUtil.NON_NLS, false);
    }
}