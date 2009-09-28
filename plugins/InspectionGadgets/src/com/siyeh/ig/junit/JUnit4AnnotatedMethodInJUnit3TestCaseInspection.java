/*
 * Copyright 2008-2009 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class JUnit4AnnotatedMethodInJUnit3TestCaseInspection extends
        BaseInspection {

    @Override
    @Nls
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "junit4.test.method.in.class.extending.junit3.testcase.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "junit4.test.method.in.class.extending.junit3.testcase.problem.descriptor");
    }

    @NotNull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        final String className = (String) infos[0];
        if (className != null) {
            return new InspectionGadgetsFix[] {
                    new RemoveTestAnnotationFix(),
                    new RemoveExtendsTestCaseFix(className)
            };
        } else {
            return new InspectionGadgetsFix[] {
                    new RemoveTestAnnotationFix()
            };
        }
    }

    private static class RemoveExtendsTestCaseFix extends InspectionGadgetsFix {
        private final String className;

        RemoveExtendsTestCaseFix(String className) {
            this.className = className;
        }

        @NotNull
        public String getName() {
            return "remove 'extends TestCase' from class '" + className + '\'';
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiMember)) {
                return;
            }
            final PsiMember method = (PsiMember) parent;
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            final PsiReferenceList extendsList =
                    containingClass.getExtendsList();
            if (extendsList == null) {
                return;
            }
            extendsList.delete();
        }
    }


    private static class RemoveTestAnnotationFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return "Remove @Test annotation";
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiModifierListOwner)) {
                return;
            }
            final PsiModifierListOwner method = (PsiModifierListOwner) parent;
            final PsiModifierList modifierList = method.getModifierList();
            if (modifierList == null) {
                return;
            }
            final PsiAnnotation annotation =
                    modifierList.findAnnotation("org.junit.Test");
            if (annotation == null) {
                return;
            }
            annotation.delete();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Junit4AnnotatedMethodInJunit3TestCaseVisitor();
    }

    private static class Junit4AnnotatedMethodInJunit3TestCaseVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (!TestUtils.isJUnit4TestMethod(method)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!TestUtils.isJUnitTestClass(containingClass)) {
                return;
            }
            final String className = containingClass.getName();
            registerMethodError(method, className);
        }
    }
}
