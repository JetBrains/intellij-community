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
package com.siyeh.ig.jdk15;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class UnnecessaryBoxingInspection extends BaseInspection {

    @NonNls static final Map<String, String> s_boxingArgs =
            new HashMap<String, String>(9);

    static {
        s_boxingArgs.put("java.lang.Integer", "int");
        s_boxingArgs.put("java.lang.Short", "short");
        s_boxingArgs.put("java.lang.Boolean", "boolean");
        s_boxingArgs.put("java.lang.Long", "long");
        s_boxingArgs.put("java.lang.Byte", "byte");
        s_boxingArgs.put("java.lang.Float", "float");
        s_boxingArgs.put("java.lang.Double", "double");
        s_boxingArgs.put("java.lang.Long", "long");
        s_boxingArgs.put("java.lang.Character", "char");
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.boxing.display.name");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.boxing.problem.descriptor");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryBoxingFix();
    }

    private static class UnnecessaryBoxingFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.boxing.remove.quickfix");
        }

        @Override
        public void doFix(@NotNull Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiCallExpression expression =
                    (PsiCallExpression)descriptor.getPsiElement();
            final PsiType boxedType = expression.getType();
            if (boxedType == null) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiType argumentType = arguments[0].getType();
            if (argumentType == null) {
                return;
            }
            final String cast = getCastString(argumentType, boxedType);
            final String newExpression = arguments[0].getText();
            replaceExpression(expression, cast + newExpression);
        }

        private static String getCastString(@NotNull PsiType fromType,
                                            @NotNull PsiType toType) {
            final String toTypeText = toType.getCanonicalText();
            final String fromTypeText = fromType.getCanonicalText();
            final String unboxedType = s_boxingArgs.get(toTypeText);
            if (fromTypeText.equals(unboxedType)) {
                return "";
            } else {
                return '(' + unboxedType + ')';
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryBoxingVisitor();
    }

    private static class UnnecessaryBoxingVisitor
            extends BaseInspectionVisitor {

        @Override public void visitNewExpression(
                @NotNull PsiNewExpression expression) {
            if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
                return;
            }
            super.visitNewExpression(expression);
            final PsiType constructorType = expression.getType();
            if (constructorType == null) {
                return;
            }
            final String constructorTypeText =
                    constructorType.getCanonicalText();
            if (!s_boxingArgs.containsKey(constructorTypeText)) {
                return;
            }
            final PsiMethod constructor = expression.resolveConstructor();
            if (constructor == null) {
                return;
            }
            final PsiParameterList parameterList =
                    constructor.getParameterList();
            if (parameterList.getParametersCount() != 1) {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiParameter arg = parameters[0];
            final PsiType argumentType = arg.getType();
            final String argumentTypeText = argumentType.getCanonicalText();
            final String boxableConstructorType =
                    s_boxingArgs.get(constructorTypeText);
            if (!boxableConstructorType.equals(argumentTypeText)) {
                return;
            }
            if (!canBeUnboxed(expression)) {
                return;
            }
            registerError(expression);
        }

        @Override public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] expressions = argumentList.getExpressions();
            if (expressions.length != 1) {
                return;
            }
            if (!(expressions[0].getType() instanceof PsiPrimitiveType)) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls
            final String referenceName = methodExpression.getReferenceName();
            if (referenceName == null || !referenceName.equals("valueOf")) {
                return;
            }
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (!(qualifierExpression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)qualifierExpression;
            final String canonicalText = referenceExpression.getCanonicalText();
            if (s_boxingArgs.get(canonicalText) == null) {
                return;
            }
            if (!canBeUnboxed(expression)) {
                return;
            }
            registerError(expression);
        }

        private static boolean canBeUnboxed(PsiExpression expression) {
            PsiElement parent = expression.getParent();
            while (parent instanceof PsiParenthesizedExpression) {
                parent = parent.getParent();
            }
            if (parent instanceof PsiExpressionStatement ||
                    parent instanceof PsiReferenceExpression) {
                return false;
            } else if (parent instanceof PsiTypeCastExpression) {
                final PsiTypeCastExpression castExpression =
                        (PsiTypeCastExpression)parent;
                final PsiType castType = castExpression.getType();
                if (castType instanceof PsiClassType) {
                    final PsiClassType classType = (PsiClassType)castType;
                    final PsiClass aClass = classType.resolve();
                    if (aClass instanceof PsiTypeParameter) {
                        return false;
                    }
                }
            } else if (parent instanceof PsiConditionalExpression) {
                final PsiConditionalExpression conditionalExpression =
                        (PsiConditionalExpression)parent;
                final PsiExpression thenExpression =
                        conditionalExpression.getThenExpression();
                final PsiExpression elseExpression =
                        conditionalExpression.getElseExpression();
                if (elseExpression == null || thenExpression == null) {
                    return false;
                }
                if (PsiTreeUtil.isAncestor(thenExpression, expression, false)) {
                    final PsiType type = elseExpression.getType();
                    return type instanceof PsiPrimitiveType;
                } else if (PsiTreeUtil.isAncestor(elseExpression, expression,
                        false)) {
                    final PsiType type = thenExpression.getType();
                    return type instanceof PsiPrimitiveType;
                } else {
                    return true;
                }
            }
            final PsiMethodCallExpression containingMethodCallExpression =
                    getParentMethodCallExpression(expression);
            return containingMethodCallExpression == null ||
                    isSameMethodCalledWithoutBoxing(
                            containingMethodCallExpression, expression);
        }

        @Nullable
        private static PsiMethodCallExpression getParentMethodCallExpression(
                @NotNull PsiElement expression) {
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiParenthesizedExpression ||
                    parent instanceof PsiExpressionList) {
                return getParentMethodCallExpression(parent);
            } else if (parent instanceof PsiMethodCallExpression) {
                return (PsiMethodCallExpression)parent;
            } else {
                return null;
            }
        }

        private static boolean isSameMethodCalledWithoutBoxing(
                @NotNull PsiMethodCallExpression methodCallExpression,
                @NotNull PsiExpression boxingExpression) {
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] expressions = argumentList.getExpressions();
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiElement element = methodExpression.resolve();
            if (!(element instanceof PsiMethod)) {
                return false;
            }
            final PsiMethod originalMethod = (PsiMethod)element;
            final String name = originalMethod.getName();
            final PsiClass containingClass =
                    originalMethod.getContainingClass();
            if (containingClass == null) {
                return false;
            }
            final PsiType[] types = new PsiType[expressions.length];
            for (int i = 0; i < expressions.length; i++) {
                final PsiExpression expression = expressions[i];
                final PsiType type = expression.getType();
                if (boxingExpression.equals(expression)) {
                    final PsiPrimitiveType unboxedType =
                            PsiPrimitiveType.getUnboxedType(type);
                    if (unboxedType == null) {
                        return false;
                    }
                    types[i] = unboxedType;
                } else {
                    types[i] = type;
                }
            }
            final PsiMethod[] methods =
                    containingClass.findMethodsByName(name, true);
            for (PsiMethod method : methods) {
                if (!originalMethod.equals(method)) {
                    if (MethodCallUtils.isApplicable(method,
                            PsiSubstitutor.EMPTY, types)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}