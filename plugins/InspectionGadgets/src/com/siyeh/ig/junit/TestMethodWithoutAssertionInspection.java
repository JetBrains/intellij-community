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

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TestMethodWithoutAssertionInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "JUnitTestMethodWithNoAssertions";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "test.method.without.assertion.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "test.method.without.assertion.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TestMethodWithoutAssertionVisitor();
    }

    private static class TestMethodWithoutAssertionVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (!TestUtils.isJUnitTestMethod(method)) {
                return;
            }
            final PsiModifierList modifierList = method.getModifierList();
            final PsiAnnotation testAnnotation =
                    modifierList.findAnnotation("org.junit.Test");
            if (testAnnotation != null) {
                final PsiAnnotationParameterList parameterList =
                        testAnnotation.getParameterList();
                final PsiNameValuePair[] nameValuePairs =
                        parameterList.getAttributes();
                for (PsiNameValuePair nameValuePair : nameValuePairs) {
                    @NonNls final String parameterName = nameValuePair.getName();
                    if ("expected".equals(parameterName)) {
                        return;
                    }
                }
            }
            final ContainsAssertionVisitor visitor =
                    new ContainsAssertionVisitor();
            method.accept(visitor);
            if (visitor.containsAssertion()) {
                return;
            }
            registerMethodError(method);
        }
    }
}