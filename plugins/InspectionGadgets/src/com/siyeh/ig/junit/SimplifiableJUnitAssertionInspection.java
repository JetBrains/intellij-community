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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SimplifiableJUnitAssertionInspection extends BaseInspection {

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
            if (isAssertTrueThatCouldBeAssertSame(callExpression)) {
                replaceAssertTrueWithAssertSame(callExpression, project);
                return;
            }
            if (isAssertTrueThatCouldBeAssertEquality(callExpression)) {
                replaceAssertTrueWithAssertEquals(callExpression, project);
            } else if (isAssertEqualsThatCouldBeAssertLiteral(callExpression)) {
                replaceAssertEqualsWithAssertLiteral(callExpression, project);
            } else if (isAssertTrueThatCouldBeFail(callExpression)) {
                replaceAssertWithFail(callExpression);
            } else if (isAssertFalseThatCouldBeFail(callExpression)) {
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
            final PsiExpression[] args = argumentList.getExpressions();
            final PsiExpression message;
            if (parameters.length == 2) {
                message = args[0];
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
            final PsiExpression[] args = argumentList.getExpressions();
            final int testPosition;
            final PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 2) {
                testPosition = 1;
                message = args[0];
            } else {
                testPosition = 0;
                message = null;
            }
            final PsiExpression testArg = args[testPosition];
            PsiExpression lhs = null;
            PsiExpression rhs = null;
            if (testArg instanceof PsiBinaryExpression) {
                lhs = ((PsiBinaryExpression)testArg).getLOperand();
                rhs = ((PsiBinaryExpression)testArg).getROperand();
            } else if (testArg instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression call =
                        (PsiMethodCallExpression)testArg;
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

        private static void replaceAssertTrueWithAssertSame(
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
            final PsiExpression[] args = argumentList.getExpressions();
            final int testPosition;
            final PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 2) {
                testPosition = 1;
                message = args[0];
            } else {
                testPosition = 0;
                message = null;
            }
            final PsiExpression testArg = args[testPosition];
            PsiExpression lhs = null;
            PsiExpression rhs = null;
            if (testArg instanceof PsiBinaryExpression) {
                lhs = ((PsiBinaryExpression)testArg).getLOperand();
                rhs = ((PsiBinaryExpression)testArg).getROperand();
            } else if (testArg instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression call =
                        (PsiMethodCallExpression)testArg;
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
            newExpression.append("assertSame(");
            if (message != null) {
                newExpression.append(message.getText());
                newExpression.append(',');
            }
            assert lhs != null;
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
            final PsiExpression[] args = argumentList.getExpressions();
            final int firstTestPosition;
            final int secondTestPosition;
            final PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 3) {
                firstTestPosition = 1;
                secondTestPosition = 2;
                message = args[0];
            } else {
                firstTestPosition = 0;
                secondTestPosition = 1;
                message = null;
            }
            final PsiExpression firstTestArg = args[firstTestPosition];
            final PsiExpression secondTestArg = args[secondTestPosition];
            final String literalValue;
            final String compareValue;
            if (isSimpleLiteral(firstTestArg)) {
                literalValue = firstTestArg.getText();
                compareValue = secondTestArg.getText();
            } else {
                literalValue = secondTestArg.getText();
                compareValue = firstTestArg.getText();
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
            if (isAssertTrueThatCouldBeAssertSame(expression)) {
                registerMethodCallError(expression);
                return;
            }
            if (isAssertTrueThatCouldBeAssertEquality(expression)) {
                registerMethodCallError(expression);
            } else if (isAssertEqualsThatCouldBeAssertLiteral(expression)) {
                registerMethodCallError(expression);
            } else if (isAssertTrueThatCouldBeFail(expression)) {
                registerMethodCallError(expression);
            } else if (isAssertFalseThatCouldBeFail(expression)) {
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
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length < 1) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        } else {
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiExpression testArg = args[testPosition];
        return testArg != null && isEqualityComparison(testArg);
    }

    static boolean isAssertTrueThatCouldBeAssertSame(
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
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length < 1) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        } else {
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiExpression testArg = args[testPosition];
        return testArg != null && isIdentityComparison(testArg);
    }

    static boolean isAssertTrueThatCouldBeFail(
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
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length < 1) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        } else {
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiExpression testArg = args[testPosition];
        return testArg != null && PsiKeyword.FALSE.equals(testArg.getText());
    }

    static boolean isAssertFalseThatCouldBeFail(
            PsiMethodCallExpression expression) {
        if (!isAssertFalse(expression)) {
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method == null) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length < 1) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        } else {
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiExpression testArg = args[testPosition];
        return testArg != null && PsiKeyword.TRUE.equals(testArg.getText());
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
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length < 2) {
            return false;
        }
        final PsiManager psiManager = expression.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
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
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiExpression firstTestArg = args[firstTestPosition];
        final PsiExpression secondTestArg = args[secondTestPosition];
        if (firstTestArg == null) {
            return false;
        }
        return secondTestArg != null && (isSimpleLiteral(firstTestArg) ||
                isSimpleLiteral(secondTestArg));
    }

    static boolean isSimpleLiteral(PsiExpression arg) {
        if (!(arg instanceof PsiLiteralExpression)) {
            return false;
        }
        final String text = arg.getText();
        return PsiKeyword.NULL.equals(text) || PsiKeyword.TRUE.equals(text) ||
                PsiKeyword.FALSE.equals(text);
    }

    private static boolean isEqualityComparison(PsiExpression testArg) {
        if (testArg instanceof PsiBinaryExpression) {
            final PsiJavaToken sign =
                    ((PsiBinaryExpression)testArg).getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.EQEQ)) {
                return false;
            }
            final PsiExpression lhs =
                    ((PsiBinaryExpression)testArg).getLOperand();
            final PsiExpression rhs =
                    ((PsiBinaryExpression)testArg).getROperand();
            if (rhs == null) {
                return false;
            }
            final PsiType type = lhs.getType();
            return type != null && ClassUtils.isPrimitive(type);
        } else if (testArg instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression)testArg;
            if (!MethodCallUtils.isEqualsCall(call)) {
                return false;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            return methodExpression.getQualifierExpression() != null;
        }
        return false;
    }

    private static boolean isIdentityComparison(PsiExpression testArg) {
        if (testArg instanceof PsiBinaryExpression) {
            final PsiBinaryExpression expression = (PsiBinaryExpression)testArg;
            final IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.EQEQ)) {
                return false;
            }
            final PsiExpression rhs =
                    expression.getROperand();
            return rhs != null;
        }
        return false;
    }

    private static boolean isAssertTrue(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (!"assertTrue".equals(methodName)) {
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

    private static boolean isAssertFalse(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (!"assertFalse".equals(methodName)) {
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

    private static boolean isAssertEquals(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (!"assertEquals".equals(methodName)) {
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