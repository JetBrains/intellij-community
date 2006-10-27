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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class TeardownCallsSuperTeardownInspection extends MethodInspection {

    public String getID() {
        return "TearDownDoesntCallSuperTearDown";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "teardown.calls.super.teardown.problem.descriptor");
    }

    private static class AddSuperTearDownCall extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "teardown.calls.super.teardown.add.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement methodName = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod)methodName.getParent();
            if (method == null) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiManager psiManager = PsiManager.getInstance(project);
            final PsiElementFactory factory =
                    psiManager.getElementFactory();
            final PsiStatement newStatement =
                    factory.createStatementFromText("super.tearDown();", null);
            final CodeStyleManager styleManager =
                    psiManager.getCodeStyleManager();
            final PsiJavaToken brace = body.getRBrace();
            body.addBefore(newStatement, brace);
            styleManager.reformat(body);
        }
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new AddSuperTearDownCall();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TeardownCallsSuperTeardownVisitor();
    }

    private static class TeardownCallsSuperTeardownVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super;
            @NonNls final String methodName = method.getName();
            if (!"tearDown".equals(methodName)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (method.getBody() == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 0) {
                return;
            }
            final PsiClass targetClass = method.getContainingClass();
            if (targetClass == null) {
                return;
            }
            if (!ClassUtils.isSubclass(targetClass,
                    "junit.framework.TestCase")) {
                return;
            }
            final CallToSuperTeardownVisitor visitor =
                    new CallToSuperTeardownVisitor();
            method.accept(visitor);
            if (visitor.isCallToSuperTeardownFound()) {
                return;
            }
            registerMethodError(method);
        }
    }
}