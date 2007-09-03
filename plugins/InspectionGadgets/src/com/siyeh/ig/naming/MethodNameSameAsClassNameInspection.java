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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class MethodNameSameAsClassNameInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "method.name.same.as.class.name.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "method.name.same.as.class.name.problem.descriptor");
    }

    @NotNull
    protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
        return new InspectionGadgetsFix[]{
                new RenameFix(), new MethodNameSameAsClassNameFix()};
    }

    private static class MethodNameSameAsClassNameFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message("make.method.ctr.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiMethod)) {
                return;
            }
            final PsiMethod method = (PsiMethod)parent;
            final PsiTypeElement returnTypeElement =
                    method.getReturnTypeElement();
            if (returnTypeElement == null) {
                return;
            }
            returnTypeElement.delete();
        }
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodNameSameAsClassNameVisitor();
    }

    private static class MethodNameSameAsClassNameVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // no call to super, so it doesn't drill down into inner classes
            if (method.isConstructor()) {
                return;
            }
            final String methodName = method.getName();
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            final String className = containingClass.getName();
            if (className == null) {
                return;
            }
            if (!methodName.equals(className)) {
                return;
            }
            registerMethodError(method);
        }
    }
}