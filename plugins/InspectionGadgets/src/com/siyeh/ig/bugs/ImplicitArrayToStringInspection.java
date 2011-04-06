/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplicitArrayToStringInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "implicit.array.to.string.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        if (((Boolean) infos[1]).booleanValue()) {
            return InspectionGadgetsBundle.message(
                    "explicit.array.to.string.problem.descriptor");
        } else if (infos[0] instanceof PsiMethodCallExpression) {
            return InspectionGadgetsBundle.message(
                    "implicit.array.to.string.method.call.problem.descriptor");
        } else {
            return InspectionGadgetsBundle.message(
                    "implicit.array.to.string.problem.descriptor");
        }
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiExpression expression = (PsiExpression)infos[0];
        final boolean removeToString = ((Boolean) infos[1]).booleanValue();
        final PsiArrayType type = (PsiArrayType) expression.getType();
        if (type != null) {
            final PsiType componentType = type.getComponentType();
            if (componentType instanceof PsiArrayType) {
                return new ImplicitArrayToStringFix(true, removeToString);
            }
        }
        return new ImplicitArrayToStringFix(false, removeToString);
    }

    private static class ImplicitArrayToStringFix extends InspectionGadgetsFix {

        private final boolean deepString;
        private final boolean removeToString;

        ImplicitArrayToStringFix(boolean deepString, boolean removeToString) {
            this.deepString = deepString;
            this.removeToString = removeToString;
        }

        @NotNull
        public String getName() {
            @NonNls final String expressionText;
            if (deepString) {
                expressionText = "java.util.Arrays.deepToString()";
            } else {
                expressionText = "java.util.Arrays.toString()";
            }
            return InspectionGadgetsBundle.message(
                    "implicit.array.to.string.quickfix", expressionText);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiExpression expression;
            if (element instanceof PsiExpression) {
                expression = (PsiExpression) element;
            }  else {
                expression = (PsiExpression) element.getParent().getParent();
            }
            final String expressionText;
            if (removeToString) {
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) expression;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (qualifier == null) {
                    return;
                }
                expressionText = qualifier.getText(); 
            } else {
                expressionText = expression.getText();
            }
            @NonNls final String newExpressionText;
            if (deepString) {
                newExpressionText =
                        "java.util.Arrays.deepToString(" + expressionText + ')';
            } else {
                newExpressionText =
                        "java.util.Arrays.toString(" + expressionText + ')';
            }
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiExpressionList) {
                final PsiElement grandParent = parent.getParent();
                if (grandParent instanceof PsiMethodCallExpression) {
                    final PsiMethodCallExpression methodCallExpression =
                            (PsiMethodCallExpression) grandParent;
                    final PsiReferenceExpression methodExpression =
                            methodCallExpression.getMethodExpression();
                    if ("valueOf".equals(methodExpression.getReferenceName())) {
                        replaceExpressionAndShorten(methodCallExpression,
                                newExpressionText);
                        return;
                    }
                }
            }
            replaceExpressionAndShorten(expression, newExpressionText);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ImplicitArrayToStringVisitor();
    }

    private static class ImplicitArrayToStringVisitor
            extends BaseInspectionVisitor {

        @Override public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (!isImplicitArrayToStringCall(expression)) {
                return;
            }
            registerError(expression, expression, Boolean.FALSE);
        }

        @Override public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (!isImplicitArrayToStringCall(expression)) {
                return;
            }
            registerError(expression, expression, Boolean.FALSE);
        }

        @Override public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (isExplicitArrayToStringCall(expression)) {
                final PsiReferenceExpression methodExpression =
                        expression.getMethodExpression();
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                registerMethodCallError(expression, qualifier, Boolean.TRUE);
                return;
            }
            if (!isImplicitArrayToStringCall(expression)) {
                return;
            }
            registerError(expression, expression, Boolean.FALSE);
        }

        private static boolean isExplicitArrayToStringCall(
                PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.TO_STRING.equals(methodName)) {
                return false;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 0) {
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            final PsiType type = qualifier.getType();
            return type instanceof PsiArrayType;
        }

        private static boolean isImplicitArrayToStringCall(
                PsiExpression expression) {
            final PsiType type = expression.getType();
            if (!(type instanceof PsiArrayType)) {
                return false;
            }
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) parent;
                final IElementType tokenType =
                        binaryExpression.getOperationTokenType();
                if (!JavaTokenType.PLUS.equals(tokenType)) {
                    return false;
                }
                final PsiExpression lhs = binaryExpression.getLOperand();
                if (!lhs.equals(expression)) {
                    final PsiType lhsType = lhs.getType();
                    return !(lhsType == null ||
                            !lhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING));
                }
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs != null && !rhs.equals(expression)) {
                    final PsiType rhsType = rhs.getType();
                    return !(rhsType == null ||
                            !rhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING));
                }
            } else if (parent instanceof PsiExpressionList) {
                final PsiExpressionList expressionList =
                        (PsiExpressionList) parent;
                final PsiElement grandParent = expressionList.getParent();
                if (!(grandParent instanceof PsiMethodCallExpression)) {
                    return false;
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) grandParent;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                @NonNls final String methodName =
                        methodExpression.getReferenceName();
                if ("valueOf".equals(methodName)) {
                    final PsiExpression[] arguments =
                            expressionList.getExpressions();
                    if (arguments.length != 1) {
                        System.out.println(1);
                        return false;
                    }
                    final PsiMethod method =
                            methodCallExpression.resolveMethod();
                    if (method == null) {
                        System.out.println(2);
                        return false;
                    }
                    final PsiClass containingClass = method.getContainingClass();
                    if (containingClass == null) {
                        System.out.println(3);
                        return false;
                    }
                    final String qualifiedName =
                            containingClass.getQualifiedName();
                    if (!"java.lang.String".equals(qualifiedName)) {
                        System.out.println(54);
                        return false;
                    }
                    return true;
                }
                if (!"print".equals(methodName) &&
                        !"println".equals(methodName)) {
                    if (!"printf".equals(methodName) &&
                        !"format".equals(methodName)) {
                        return false;
                    } else {
                        final PsiExpression[] arguments =
                                expressionList.getExpressions();
                        if (arguments.length < 1) {
                            return false;
                        }
                        final PsiMethod method =
                                methodCallExpression.resolveMethod();
                        if (method == null) {
                            return false;
                        }
                        final PsiParameterList parameterList =
                                method.getParameterList();
                        final PsiParameter[] parameters =
                                parameterList.getParameters();
                        final PsiParameter parameter = parameters[0];
                        final PsiType firstParameterType = parameter.getType();
                        if (firstParameterType.equalsToText(
                                "java.util.Locale")) {
                            if (arguments.length < 4) {
                                return false;
                            }
                        } else {
                            if (arguments.length < 3) {
                                return false;
                            }
                        }
                    }
                }
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        "java.io.PrintStream", "java.util.Formatter",
                        "java.io.PrintWriter") == null) {
                    if (!(qualifier instanceof PsiReferenceExpression)) {
                        return false;
                    }
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression)qualifier;
                    final PsiElement target = referenceExpression.resolve();
                    if (!(target instanceof PsiClass)) {
                        return false;
                    }
                    final PsiClass aClass = (PsiClass)target;
                    final String className = aClass.getQualifiedName();
                    if (!CommonClassNames.JAVA_LANG_STRING.equals(className)) {
                        return false;
                    }
                }
                final PsiArrayType arrayType = (PsiArrayType)type;
                final PsiType componentType = arrayType.getComponentType();
                return !componentType.equals(PsiType.CHAR);
            }
            return false;
        }
    }
}