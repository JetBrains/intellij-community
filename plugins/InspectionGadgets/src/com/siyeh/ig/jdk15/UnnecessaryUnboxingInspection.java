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
package com.siyeh.ig.jdk15;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class UnnecessaryUnboxingInspection extends ExpressionInspection {

    @NonNls static final Map<String, String> s_unboxingMethods =
            new HashMap<String, String>(9);

    static {
        s_unboxingMethods.put("java.lang.Integer", "intValue");
        s_unboxingMethods.put("java.lang.Short", "shortValue");
        s_unboxingMethods.put("java.lang.Boolean", "booleanValue");
        s_unboxingMethods.put("java.lang.Long", "longValue");
        s_unboxingMethods.put("java.lang.Byte", "byteValue");
        s_unboxingMethods.put("java.lang.Float", "floatValue");
        s_unboxingMethods.put("java.lang.Long", "longValue");
        s_unboxingMethods.put("java.lang.Double", "doubleValue");
        s_unboxingMethods.put("java.lang.Character", "charValue");
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.unboxing.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.unboxing.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryUnboxingVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new UnnecessaryUnboxingFix();
    }

    private static class UnnecessaryUnboxingFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.unboxing.remove.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression)descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    methodCall.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            final PsiExpression strippedQualifier =
                    ParenthesesUtils.stripParentheses(qualifier);
            if (strippedQualifier == null) {
                return;
            }
            if (strippedQualifier instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)strippedQualifier;
                final PsiElement element = referenceExpression.resolve();
                if (element instanceof PsiField) {
                    final PsiField field = (PsiField)element;
                    final PsiClass containingClass = field.getContainingClass();
                    final String classname = containingClass.getQualifiedName();
                    if ("java.lang.Boolean".equals(classname)) {
                        @NonNls final String name = field.getName();
                        if ("TRUE".equals(name)) {
                            replaceExpression(methodCall, "true");
                            return;
                        } else if ("FALSE".equals(name)) {
                            replaceExpression(methodCall, "false");
                            return;
                        }
                    }
                }
            }
            final String strippedQualifierText = strippedQualifier.getText();
            replaceExpression(methodCall, strippedQualifierText);
        }
    }

    private static class UnnecessaryUnboxingVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
            if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                return;
            }
            if (!isUnboxingExpression(expression)) {
                return;
            }
            final PsiExpression containingExpression =
                    getContainingExpression(expression);
            if (containingExpression instanceof PsiTypeCastExpression) {
                return;
            }
            if (isPossibleObjectComparison(expression, containingExpression)) {
                return;
            }
            if (containingExpression instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression)containingExpression;
                if (!isSameMethodCalledWithoutUnboxing(methodCallExpression,
                        expression)) {
                    return;
                }
            }
            registerError(expression);
        }

        private static boolean isPossibleObjectComparison(
                PsiMethodCallExpression expression,
                PsiExpression containingExpression) {
            if (!(containingExpression instanceof PsiBinaryExpression)) {
                return false;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)containingExpression;
            final PsiJavaToken operationSign =
                    binaryExpression.getOperationSign();
            final IElementType tokenType = operationSign.getTokenType();
            if (!JavaTokenType.EQEQ.equals(tokenType) &&
                    !JavaTokenType.NE.equals(tokenType)) {
                return false;
            }
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return true;
            }
            if (expression == lhs) {
                if (!(rhs.getType() instanceof PsiPrimitiveType) ||
                        isUnboxingExpression(rhs)) {
                    return true;
                }
            }
            if (expression == rhs) {
                if (!(lhs.getType() instanceof PsiPrimitiveType) ||
                        isUnboxingExpression(lhs)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isUnboxingExpression(
                PsiExpression expression) {
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)expression;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            final PsiType qualifierType = qualifier.getType();
            if (qualifierType == null) {
                return false;
            }
            final String qualifierTypeName = qualifierType.getCanonicalText();
            if (!s_unboxingMethods.containsKey(qualifierTypeName)) {
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            final String unboxingMethod =
                    s_unboxingMethods.get(qualifierTypeName);
            return unboxingMethod.equals(methodName);
        }

        private static boolean isSameMethodCalledWithoutUnboxing(
                @NotNull PsiMethodCallExpression methodCallExpression,
                @NotNull PsiMethodCallExpression unboxingExpression) {
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
            final PsiType[] types = new PsiType[expressions.length];
            for (int i = 0; i < expressions.length; i++) {
                final PsiExpression expression = expressions[i];
                final PsiType type = expression.getType();
                if (unboxingExpression.equals(expression)) {
                    if (!(type instanceof PsiPrimitiveType)) {
                        return false;
                    }
                    final PsiPrimitiveType primitiveType =
                            (PsiPrimitiveType)type;
                    types[i] = primitiveType.getBoxedType(unboxingExpression);
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

        @Nullable
        private static PsiExpression getContainingExpression(
                @NotNull PsiElement expression) {
            final PsiElement parent = expression.getParent();
            if (parent == null) {
                return null;
            } else if (!(parent instanceof PsiExpression) &&
                    !(parent instanceof PsiExpressionList)) {
                return null;
            }
            if (parent instanceof PsiParenthesizedExpression ||
                    parent instanceof PsiConditionalExpression ||
                    parent instanceof PsiExpressionList) {
                return getContainingExpression(parent);
            } else {
                return (PsiExpression)parent;
            }
        }
    }
}