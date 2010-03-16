/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class TypeUtils {

    private TypeUtils() {}

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
        return targetType != null && targetType.equalsToText(typeName);
    }

    public static PsiType getJavaLangObjectType(@NotNull PsiElement context) {
        final Project project = context.getProject();
        final PsiElementFactory factory =
                JavaPsiFacade.getInstance(project).getElementFactory();
        return factory.createTypeFromText("java.lang.Object", context);
    }

    public static boolean isJavaLangObject(@Nullable PsiType targetType) {
        return typeEquals("java.lang.Object", targetType);
    }

    public static boolean isJavaLangString(@Nullable PsiType targetType) {
        return typeEquals("java.lang.String", targetType);
    }

    public static boolean expressionHasTypeOrSubtype(
            @Nullable PsiExpression expression,
            @NonNls @NotNull String typeName) {
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
        return aClass != null && ClassUtils.isSubclass(aClass, typeName);
    }

    //getTypeIfOneOfOrSubtype
    public static String expressionHasTypeOrSubtype(
            @Nullable PsiExpression expression,
            @NonNls @NotNull String... typeNames) {
        if (expression == null) {
            return null;
        }
        final PsiType type = expression.getType();
        if (type == null) {
            return null;
        }
        if (!(type instanceof PsiClassType)) {
            return null;
        }
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass aClass = classType.resolve();
        if (aClass == null) {
            return null;
        }
        for (String typeName : typeNames) {
            if (ClassUtils.isSubclass(aClass, typeName)) {
                return typeName;
            }
        }
        return null;
    }

    public static boolean expressionHasTypeOrSubtype(
            @Nullable PsiExpression expression,
            @NonNls @NotNull Collection<String> typeNames) {
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