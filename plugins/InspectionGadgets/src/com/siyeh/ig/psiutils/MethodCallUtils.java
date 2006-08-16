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
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodCallUtils {

    private MethodCallUtils() {
        super();
    }

    @Nullable
    public static String getMethodName(
            @NotNull PsiMethodCallExpression expression) {
        final PsiReferenceExpression method = expression.getMethodExpression();
        return method.getReferenceName();
    }

    @Nullable
    public static PsiType getTargetType(
            @NotNull PsiMethodCallExpression expression) {
        final PsiReferenceExpression method = expression.getMethodExpression();
        final PsiExpression qualifierExpression =
                method.getQualifierExpression();
        if (qualifierExpression == null) {
            return null;
        }
        return qualifierExpression.getType();
    }

    public static boolean isEqualsCall(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiElement element = methodExpression.resolve();
        if (!(element instanceof PsiMethod)) {
            return false;
        }
        final PsiMethod method = (PsiMethod)element;
        return MethodUtils.isEquals(method);
    }

    public static boolean isCallToMethod(
            @NotNull PsiMethodCallExpression expression,
            @NotNull String containingClassName,
            @NotNull PsiType returnType,
            @NotNull String methodName,
            PsiType... parameterTypes) {
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
            return false;
        }
        return MethodUtils.methodMatches(method, containingClassName, returnType,
                methodName, parameterTypes);
    }

    public static boolean isApplicable(PsiMethod method,
                                       PsiSubstitutor substitutorForMethod,
                                       PsiType[] types) {
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        if (method.isVarArgs()) {
            if (types.length < parameters.length - 1) {
                return false;
            }
            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            PsiType lastParameterType = lastParameter.getType();
            if (!(lastParameterType instanceof PsiArrayType)) {
                return false;
            }
            lastParameterType = substitutorForMethod.substituteAndCapture(
                    lastParameterType);
            if (lastParameter.isVarArgs()) {
                for (int i = 0; i < parameters.length - 1; i++) {
                    final PsiParameter parm = parameters[i];
                    if (parm.isVarArgs()) {
                        return false;
                    }
                    final PsiType argType = types[i];
                    if (argType == null) {
                        return false;
                    }
                    final PsiType parameterType = parameters[i].getType();
                    final PsiType substitutedParmType =
                            substitutorForMethod.substituteAndCapture(
                                    parameterType);
                    if (!TypeConversionUtil.isAssignable(substitutedParmType,
                            argType)) {
                        return false;
                    }
                }
                if (types.length == parameters.length) {
                    //call with array as vararg parameter
                    final PsiType lastArgType = types[types.length - 1];
                    if (lastArgType != null && TypeConversionUtil.isAssignable(
                            lastParameterType, lastArgType)) {
                        return true;
                    }
                }
                final PsiArrayType arrayType = (PsiArrayType)lastParameterType;
                final PsiType componentType = arrayType.getComponentType();
                for (int i = parameters.length - 1; i < types.length; i++) {
                    final PsiType argType = types[i];
                    if (argType == null ||
                            !TypeConversionUtil.isAssignable(componentType,
                                    argType)) {
                        return false;
                    }
                }
            } else {
                return false;
            }
        } else {
            if (types.length != parameters.length) {
                return false;
            }
            for (int i = 0; i < types.length; i++) {
                final PsiType type = types[i];
                if (type == null) {
                    return false; //?
                }
                final PsiType parameterType = parameters[i].getType();
                final PsiType substitutedParameterType =
                        substitutorForMethod.substituteAndCapture(parameterType);
                if (!TypeConversionUtil.isAssignable(substitutedParameterType,
                        type)) {
                    return false;
                }
            }
        }
        return true;
    }
}