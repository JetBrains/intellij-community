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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

public class TypeUtils {

    private TypeUtils() {
        super();
    }

    @Nullable
    public static PsiClassType calculateWeakestTypeNecessary(
            PsiVariable variable) {
        final PsiType variableType = variable.getType();
        if (!(variableType instanceof PsiClassType)) {
            return null;
        }
        final PsiClassType variableClassType = (PsiClassType) variableType;
        final PsiClass variableClass = variableClassType.resolve();

        final PsiManager manager = variable.getManager();
        final PsiElementFactory factory = manager.getElementFactory();
        final GlobalSearchScope scope = variable.getResolveScope();
        PsiClassType weakestType = PsiType.getJavaLangObject(manager, scope);
        PsiClass weakestTypeClass = weakestType.resolve();
        if (weakestTypeClass == null) {
            return null;
        }
        final Query<PsiReference> query = ReferencesSearch.search(variable);
        final Collection<PsiReference> references = query.findAll();
        for (PsiReference reference : references) {
            if (weakestType.equals(variableClass)) {
                return null;
            }
            final PsiElement referenceElement = reference.getElement();
            final PsiElement referenceParent = referenceElement.getParent();
            final PsiElement referenceGrandParent = referenceParent.getParent();
            if (referenceParent instanceof PsiExpressionList) {
                if (!(referenceGrandParent instanceof
                        PsiMethodCallExpression)) {
                    return null;
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) referenceGrandParent;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final PsiElement methodElement = methodExpression.resolve();
                if (!(methodElement instanceof PsiMethod)) {
                    return null;
                }
                final PsiMethod method = (PsiMethod) methodElement;
                if (!(referenceElement instanceof PsiExpression)) {
                    return null;
                }
                final PsiExpression expression =
                        (PsiExpression) referenceElement;
                final PsiExpressionList expressionList =
                        (PsiExpressionList)referenceParent;
                final int index =
                        getExpressionIndex(expression, expressionList);
                final PsiParameterList parameterList =
                        method.getParameterList();
                final PsiParameter[] parameters =
                        parameterList.getParameters();
                final PsiParameter parameter = parameters[index];
                final PsiType type = parameter.getType();
                if (!(type instanceof PsiClassType)) {
                    return null;
                }
                final PsiClassType classType = (PsiClassType) type;
                final PsiClass aClass = classType.resolve();
                if (aClass == null || weakestType.equals(aClass)) {
                    return null;
                }
                if (aClass.isInheritor(weakestTypeClass, true)) {
                    weakestTypeClass = aClass;
                    weakestType = classType;
                }
            } else if (referenceGrandParent instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) referenceGrandParent;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final PsiElement methodElement = methodExpression.resolve();
                if (!(methodElement instanceof PsiMethod)) {
                    return null;
                }
                PsiMethod method = (PsiMethod) methodElement;
                final PsiMethod[] superMethods =
                        method.findDeepestSuperMethods();
                if (superMethods.length > 0) {
                    // todo figure out the course of action if the method
                    // overrides/implements multiple interface method and/or
                    // a class method and an interface method.
                    method = superMethods[0];
                }
                final PsiClass containingClass =
                        method.getContainingClass();
                if (containingClass.equals(weakestType)) {
                    return null;
                }
                if (containingClass.isInheritor(weakestTypeClass, true)) {
                    weakestType = factory.createType(containingClass);
                    weakestTypeClass = containingClass;
                }
            } else if (referenceParent instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression assignmentExpression =
                        (PsiAssignmentExpression) referenceParent;
                final PsiExpression lhs =
                        assignmentExpression.getLExpression();
                final PsiExpression rhs =
                        assignmentExpression.getRExpression();
                if (referenceElement.equals(lhs) && rhs != null) {
                    final PsiType type = rhs.getType();
                    if (!(type instanceof PsiClassType)) {
                        return null;
                    }
                    final PsiClassType classType = (PsiClassType) type;
                    final PsiClass aClass = classType.resolve();
                    if (aClass == null || weakestType.equals(aClass)) {
                        return null;
                    }
                    if (aClass.isInheritor(weakestTypeClass, true)) {
                        weakestTypeClass = aClass;
                        weakestType = classType;
                    }
                } else if (referenceElement.equals(rhs)) {
                    final PsiType type = lhs.getType();
                    if (!(type instanceof PsiClassType)) {
                        return null;
                    }
                    final PsiClassType classType = (PsiClassType) type;
                    final PsiClass aClass = classType.resolve();
                    if (aClass == null || weakestType.equals(aClass)) {
                        return null;
                    }
                    if (aClass.isInheritor(weakestTypeClass, true)) {
                        weakestTypeClass = aClass;
                        weakestType = classType;
                    }
                }
            }
        }
        return weakestType;
    }

    public static int getExpressionIndex(
            @NotNull PsiExpression expression,
            @NotNull PsiExpressionList expressionList) {
        final PsiExpression[] expressions = expressionList.getExpressions();
        for (int i = 0; i < expressions.length; i++) {
            final PsiExpression anExpression = expressions[i];
            if (expression.equals(anExpression)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean expressionHasType(
            @NonNls @NotNull String typeName,
            @Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        return typeEquals(typeName, type);
    }

    public static boolean typeEquals(@NonNls @NotNull String typeName,
                                     @Nullable PsiType targetType) {
        if (targetType == null) {
            return false;
        }
        return targetType.equalsToText(typeName);
    }

    public static boolean isJavaLangObject(@Nullable PsiType targetType) {
        return typeEquals("java.lang.Object", targetType);
    }

    public static boolean isJavaLangString(@Nullable PsiType targetType) {
        return typeEquals("java.lang.String", targetType);
    }

    public static boolean expressionHasTypeOrSubtype(
            @NonNls @NotNull String typeName,
            @Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        if (type == null) {
            return false;
        }
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass aClass = classType.resolve();
        if (aClass == null) {
            return false;
        }
        return ClassUtils.isSubclass(aClass, typeName);
    }

    public static boolean expressionHasTypeOrSubtype(
            @Nullable PsiExpression expression,
            @NonNls @NotNull String... typeNames) {
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        if (type == null) {
            return false;
        }
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass aClass = classType.resolve();
        if (aClass == null) {
            return false;
        }
        for (String typeName : typeNames) {
            if (ClassUtils.isSubclass(aClass, typeName)) {
                return true;
            }
        }
        return false;
    }
}