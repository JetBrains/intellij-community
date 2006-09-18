/*
 * Copyright 2006 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class TestMethodInProductCodeInspection extends MethodInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "test.method.in.product.code.display.name");
    }

    @NotNull
    public String getID() {
        return "JUnitTestMethodInProductSource";
    }

    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "test.method.in.product.code.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TestCaseInProductCodeVisitor();
    }

    private static class TestCaseInProductCodeVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(PsiMethod method) {
            final PsiClass containingClass = method.getContainingClass();
            if (TestUtils.isTest(containingClass)) {
                return;
            }
            if (!AnnotationUtil.isAnnotated(method, "org.junit.Test", true)) {
                return;
            }
            registerMethodError(method);
        }
    }
}