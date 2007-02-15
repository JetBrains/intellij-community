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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

public class TestCaseWithConstructorInspection extends ClassInspection {

    public String getID() {
        return "JUnitTestCaseWithNonTrivialConstructors";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "test.case.with.constructor.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        if (Boolean.TRUE.equals(infos[0])) {
            return InspectionGadgetsBundle.message(
                    "test.case.with.constructor.problem.descriptor.initializer");
        } else {
            return InspectionGadgetsBundle.message(
                    "test.case.with.constructor.problem.descriptor");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TestCaseWithConstructorVisitor();
    }

    private static class TestCaseWithConstructorVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            if (!method.isConstructor()) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (!TestUtils.isJUnitTestClass(aClass)) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (isTrivial(body)) {
                return;
            }
            registerMethodError(method, Boolean.FALSE);
        }

        public void visitClassInitializer(PsiClassInitializer initializer) {
            if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiElement parent = initializer.getParent();
            if (parent instanceof PsiClass &&
                !TestUtils.isJUnitTestClass((PsiClass) parent)) {
                return;
            }
            final PsiCodeBlock body = initializer.getBody();
            if (isTrivial(body)) {
                return;
            }
            final PsiJavaToken leftBrace = body.getLBrace();
            if (leftBrace == null) {
                return;
            }
            registerError(leftBrace, Boolean.TRUE);
        }

        private static boolean isTrivial(PsiCodeBlock codeBlock) {
            if (codeBlock == null) {
                return true;
            }
            final PsiStatement[] statements = codeBlock.getStatements();
            if (statements.length == 0) {
                return true;
            }
            if (statements.length > 1) {
                return false;
            }
            final PsiStatement statement = statements[0];
            if (!(statement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement)statement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression)expression;
            final PsiReferenceExpression ref = call.getMethodExpression();
            final String text = ref.getText();
            return PsiKeyword.SUPER.equals(text);
        }
    }
}
