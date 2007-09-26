/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public class SimplifiableJUnitAssertionInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "simplifiable.junit.assertion.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "simplifiable.junit.assertion.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new SimplifyJUnitAssertFix();
    }

    private static class SimplifyJUnitAssertFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "simplify.j.unit.assertion.simplify.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiElement parent = methodNameIdentifier.getParent();
            assert parent != null;
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression)parent.getParent();
            if (isAssertThatCouldBeAssertNull(callExpression)) {
                replaceAssertWithAssertNull(callExpression, project);
            } else  if (isAssertThatCouldBeAssertSame(callExpression)) {
                replaceAssertWithAssertSame(callExpression, project);
            } else if (isAssertTrueThatCouldBeAssertEquality(callExpression)) {
                replaceAssertTrueWithAssertEquals(callExpression, project);
            } else if (isAssertEqualsThatCouldBeAssertLiteral(callExpression)) {
                replaceAssertEqualsWithAssertLiteral(callExpression, project);
            } else if (isAssertThatCouldBeFail(callExpression)) {
                replaceAssertWithFail(callExpression);
            }
        }

        private static void replaceAssertWithFail(
                PsiMethodCallExpression callExpression)
                throws IncorrectOperationException {
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();
            final PsiMethod method = (PsiMethod)methodExpression.resolve();
            assert method != null;
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            final PsiExpression message;
            if (parameters.length == 2) {
                message = arguments[0];
            } else {
                message = null;
            }
            @NonNls final StringBuilder newExpression = new StringBuilder();
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(callExpression, PsiMethod.class);
            if (containingMethod != null &&
                    AnnotationUtil.isAnnotated(containingMethod,
                            "org.junit.Test", true)) {
                newExpression.append("org.junit.Assert.");
            }
            newExpression.append("fail(");
            if (message != null) {
                newExpression.append(message.getText());
            }
            newExpression.append(')');
            replaceExpressionAndShorten(callExpression,
                    newExpression.toString());
        }

        private static void replaceAssertTrueWithAssertEquals(
                PsiMethodCallExpression callExpression, Project project)
                throws IncorrectOperationException {
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();
            final PsiMethod method = (PsiMethod)methodExpression.resolve();
            assert method != null;
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiManager psiManager = callExpression.getManager();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType =
                    PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            final int testPosition;
            final PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 2) {
                testPosition = 1;
                message = arguments[0];
            } else {
                testPosition = 0;
                message = null;
            }
            final PsiExpression testArgument = arguments[testPosition];
            PsiExpression lhs = null;
            PsiExpression rhs = null;
            if (testArgument instanceof PsiBinaryExpression) {
                lhs = ((PsiBinaryExpression)testArgument).getLOperand();
                rhs = ((PsiBinaryExpression)testArgument).getROperand();
            } else if (testArgument instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression call =
                        (PsiMethodCallExpression)testArgument;
                final PsiReferenceExpression equalityMethodExpression =
                        call.getMethodExpression();
                final PsiExpressionList equalityArgumentList =
                        call.getArgumentList();
                final PsiExpression[] equalityArgs =
                        equalityArgumentList.getExpressions();
                rhs = equalityArgs[0];
                lhs = equalityMethodExpression.getQualifierExpression();
            }
            if (!(lhs instanceof PsiLiteralExpression) &&
                    rhs instanceof PsiLiteralExpression) {
                final PsiExpression temp = lhs;
                lhs = rhs;
                rhs = temp;
            }
            @NonNls final StringBuilder newExpression = new StringBuilder();
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(
                    callExpression, PsiMethod.class);
            if (containingMethod != null && AnnotationUtil.isAnnotated(
                    containingMethod, "org.junit.Test", true)) {
                newExpression.append("org.junit.Assert.");
            }
            newExpression.append("assertEquals(");
            if (message != null) {
                newExpression.append(message.getText());
                newExpression.append(',');
            }
            assert lhs != null;
            newExpression.append(lhs.getText());
            newExpression.append(',');
            assert rhs != null;
            newExpression.append(rhs.getText());
            if (isFloatingPoint(lhs) || isFloatingPoint(rhs)) {
                newExpression.append(",0.0");
            }
            newExpression.append(')');
            replaceExpressionAndShorten(callExpression,
                    newExpression.toString());
        }

        private static void replaceAssertWithAssertNull(
                PsiMethodCallExpression callExpression, Project project)
                throws IncorrectOperationException {
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();
            final PsiMethod method = (PsiMethod)methodExpression.resolve();
            assert method != null;
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiManager psiManager = callExpression.getManager();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType =
                    PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            final int testPosition;
            final PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 2) {
                testPosition = 1;
                message = arguments[0];
            } else {
                testPosition = 0;
                message = null;
            }
            final PsiExpression testArgument = arguments[testPosition];
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)testArgument;
            final PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return;
            }
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            if (!(lhs instanceof PsiLiteralExpression) &&
                    rhs instanceof PsiLiteralExpression) {
                rhs = lhs;
            }
            @NonNls final StringBuilder newExpression = new StringBuilder();
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(
                    callExpression, PsiMethod.class);
            if (containingMethod != null && AnnotationUtil.isAnnotated(
                    containingMethod, "org.junit.Test", true)) {
                newExpression.append("org.junit.Assert.");
            }
            final String methodName = methodExpression.getReferenceName();
            if ("assertFalse".equals(methodName) ^
                    tokenType.equals(JavaTokenType.NE)) {
                newExpression.append("assertNotNull(");
            } else {
                newExpression.append("assertNull(");
            }
            if (message != null) {
                newExpression.append(message.getText());
                newExpression.append(',');
            }
            newExpression.append(rhs.getText());
            newExpression.append(')');
            replaceExpressionAndShorten(callExpression,
                    newExpression.toString());
        }

        private static void replaceAssertWithAssertSame(
                PsiMethodCallExpression callExpression, Project project)
                throws IncorrectOperationException {
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();
            final PsiMethod method = (PsiMethod)methodExpression.resolve();
            assert method != null;
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiManager psiManager = callExpression.getManager();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType =
                    PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            final int testPosition;
            final PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 2) {
                testPosition = 1;
                message = arguments[0];
            } else {
                testPosition = 0;
                message = null;
            }
            final PsiExpression testArgument = arguments[testPosition];
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)testArgument;
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            if (!(lhs instanceof PsiLiteralExpression) &&
                    rhs instanceof PsiLiteralExpression) {
                final PsiExpression temp = lhs;
                lhs = rhs;
                rhs = temp;
            }
            @NonNls final StringBuilder newExpression = new StringBuilder();
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(
                    callExpression, PsiMethod.class);
            if (containingMethod != null && AnnotationUtil.isAnnotated(
                    containingMethod, "org.junit.Test", true)) {
                newExpression.append("org.junit.Assert.");
            }
            final String methodName = methodExpression.getReferenceName();
            if ("assertFalse".equals(methodName) ^
                    tokenType.equals(JavaTokenType.NE)) {
                newExpression.append("assertNotSame(");
            } else {
                newExpression.append("assertSame(");
            }
            if (message != null) {
                newExpression.append(message.getText());
                newExpression.append(',');
            }
            newExpression.append(lhs.getText());
            newExpression.append(',');
            assert rhs != null;
            newExpression.append(rhs.getText());
            newExpression.append(')');
            replaceExpressionAndShorten(callExpression,
                    newExpression.toString());
        }

        private static void replaceAssertEqualsWithAssertLiteral(
                PsiMethodCallExpression callExpression, Project project)
                throws IncorrectOperationException {
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();
            final PsiMethod method = (PsiMethod)methodExpression.resolve();
            assert method != null;
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiManager psiManager = callExpression.getManager();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType =
                    PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            final int firstTestPosition;
            final int secondTestPosition;
            final PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 3) {
                firstTestPosition = 1;
                secondTestPosition = 2;
                message = arguments[0];
            } else {
                firstTestPosition = 0;
                secondTestPosition = 1;
                message = null;
            }
            final PsiExpression firstTestArgument = arguments[firstTestPosition];
            final PsiExpression secondTestArgument = arguments[secondTestPosition];
            final String literalValue;
            final String compareValue;
            if (isSimpleLiteral(firstTestArgument)) {
                literalValue = firstTestArgument.getText();
                compareValue = secondTestArgument.getText();
            } else {
                literalValue = secondTestArgument.getText();
                compareValue = firstTestArgument.getText();
            }
            final String uppercaseLiteralValue =
                    Character.toUpperCase(literalValue.charAt(0)) +
                            literalValue.substring(1);
            @NonNls final StringBuilder newExpression = new StringBuilder();
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(
                    callExpression, PsiMethod.class);
            if (containingMethod != null && AnnotationUtil.isAnnotated(
                    containingMethod, "org.junit.Test", true)) {
                newExpression.append("org.junit.Assert.");
            }
            newExpression.append("assert");
            newExpression.append(uppercaseLiteralValue);
            newExpression.append('(');
            if (message != null) {
                newExpression.append(message.getText());
                newExpression.append(',');
            }
            newExpression.append(compareValue);
            newExpression.append(')');
            replaceExpressionAndShorten(callExpression,
                    newExpression.toString());
        }

        private static boolean isFloatingPoint(PsiExpression expression) {
            final PsiType type = expression.getType();
            return PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SimplifiableJUnitAssertionVisitor();
    }

    private static class SimplifiableJUnitAssertionVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (isAssertThatCouldBeAssertNull(expression)) {
                registerMethodCallError(expression);
            } else if (isAssertThatCouldBeAssertSame(expression)) {
                registerMethodCallError(expression);
            } else if (isAssertTrueThatCouldBeAssertEquality(expression)) {
                registerMethodCallError(expression);
            } else if (isAssertEqualsThatCouldBeAssertLiteral(expression)) {
                registerMethodCallError(expression);
            } else if (isAssertThatCouldBeFail(expression)) {
                registerMethodCallError(expression);
            }
        }
    }

    static boolean isAssertTrueThatCouldBeAssertEquality(
            PsiMethodCallExpression expression) {
        if (!isAssertTrue(expression)) {
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method == null) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 1) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        } else {
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression testArgument = arguments[testPosition];
        return testArgument != null && isEqualityComparison(testArgument);
    }

    static boolean isAssertThatCouldBeAssertSame(
            PsiMethodCallExpression expression) {
        if (!isAssertTrue(expression) && !isAssertFalse(expression)) {
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method == null) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 1) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        } else {
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression testArgument = arguments[testPosition];
        return testArgument != null && isIdentityComparison(testArgument);
    }

    static boolean isAssertThatCouldBeAssertNull(
            PsiMethodCallExpression expression) {
        if (!isAssertTrue(expression) && !isAssertFalse(expression)) {
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method == null) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 1) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        } else {
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression testArgument = arguments[testPosition];
        return testArgument != null && isNullComparison(testArgument);
    }


    static boolean isAssertThatCouldBeFail(PsiMethodCallExpression expression) {
        final boolean checkTrue;
        if (isAssertFalse(expression)) {
            checkTrue = true;
        } else if (isAssertTrue(expression)) {
            checkTrue = false;
        } else {
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method == null) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 1) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        } else {
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression testArgument = arguments[testPosition];
        if (testArgument == null) {
            return false;
        }
        final String testArgumentText = testArgument.getText();
        if (checkTrue) {
            return PsiKeyword.TRUE.equals(testArgumentText);
        } else {
            return PsiKeyword.FALSE.equals(testArgumentText);
        }
    }

    static boolean isAssertEqualsThatCouldBeAssertLiteral(
            PsiMethodCallExpression expression) {
        if (!isAssertEquals(expression)) {
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method == null) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 2) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiType paramType1 = parameters[0].getType();
        final int firstTestPosition;
        final int secondTestPosition;
        if (paramType1.equals(stringType) && parameters.length > 2) {
            firstTestPosition = 1;
            secondTestPosition = 2;
        } else {
            firstTestPosition = 0;
            secondTestPosition = 1;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression firstTestArgument = arguments[firstTestPosition];
        final PsiExpression secondTestArgument = arguments[secondTestPosition];
        if (firstTestArgument == null) {
            return false;
        }
        return secondTestArgument != null && (isSimpleLiteral(firstTestArgument) ||
                isSimpleLiteral(secondTestArgument));
    }

    static boolean isSimpleLiteral(PsiExpression expression) {
        if (!(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        final String text = expression.getText();
        return PsiKeyword.NULL.equals(text) || PsiKeyword.TRUE.equals(text) ||
                PsiKeyword.FALSE.equals(text);
    }

    private static boolean isEqualityComparison(PsiExpression expression) {
        if (expression instanceof PsiBinaryExpression) {
            final PsiJavaToken sign =
                    ((PsiBinaryExpression)expression).getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.EQEQ)) {
                return false;
            }
            final PsiExpression lhs =
                    ((PsiBinaryExpression)expression).getLOperand();
            final PsiExpression rhs =
                    ((PsiBinaryExpression)expression).getROperand();
            if (rhs == null) {
                return false;
            }
            final PsiType type = lhs.getType();
            return type != null && ClassUtils.isPrimitive(type);
        } else if (expression instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression)expression;
            if (!MethodCallUtils.isEqualsCall(call)) {
                return false;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            return methodExpression.getQualifierExpression() != null;
        }
        return false;
    }

    private static boolean isIdentityComparison(PsiExpression expression) {
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)expression;
        if (!ComparisonUtils.isEqualityComparison(binaryExpression)) {
            return false;
        }
        final PsiExpression rhs =
                binaryExpression.getROperand();
        if (rhs == null) {
            return false;
        }
        final LanguageLevel languageLevel =
                PsiUtil.getLanguageLevel(expression);
        if(languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0){
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiType lhsType = lhs.getType();
            if (lhsType instanceof PsiPrimitiveType) {
                return false;
            }
            final PsiType rhsType = rhs.getType();
            if (rhsType instanceof PsiPrimitiveType) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNullComparison(PsiExpression expression) {
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        if (!ComparisonUtils.isEqualityComparison(binaryExpression)) {
            return false;
        }
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
            return false;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        return PsiKeyword.NULL.equals(lhs.getText()) ||
                PsiKeyword.NULL.equals(rhs.getText());
    }

    private static boolean isAssertTrue(
            @NotNull PsiMethodCallExpression expression) {
        return isAssertMethodCall(expression, "assertTrue");
    }

    private static boolean isAssertFalse(
            @NotNull PsiMethodCallExpression expression) {
        return isAssertMethodCall(expression, "assertFalse");    }

    private static boolean isAssertEquals(
            @NotNull PsiMethodCallExpression expression) {
        return isAssertMethodCall(expression, "assertEquals");
    }

    private static boolean isAssertMethodCall(
            @NotNull PsiMethodCallExpression expression,
            @NotNull String assertMethodName) {
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (!assertMethodName.equals(methodName)) {
            return false;
        }
        final PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method == null) {
            return false;
        }
        final PsiClass targetClass = method.getContainingClass();
        if (targetClass == null) {
            return false;
        }
        return ClassUtils.isSubclass(targetClass, "junit.framework.Assert")
                || ClassUtils.isSubclass(targetClass, "org.junit.Assert");

    }
}