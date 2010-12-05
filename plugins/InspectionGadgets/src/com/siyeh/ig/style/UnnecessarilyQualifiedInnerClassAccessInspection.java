/*
 * Copyright 2010 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class UnnecessarilyQualifiedInnerClassAccessInspection 
        extends BaseInspection {

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessarily.qualified.inner.class.access.display.name");
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
        final PsiClass aClass = (PsiClass) infos[0];
        return InspectionGadgetsBundle.message(
                "unnecessarily.qualified.inner.class.access.problem.descriptor",
                aClass.getName());
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessarilyQualifiedInnerClassAccessFix();
    }

    private static class UnnecessarilyQualifiedInnerClassAccessFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessarily.qualified.inner.class.access.quickfix");
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiFile containingFile = element.getContainingFile();
            if (!(containingFile instanceof PsiJavaFile)) {
                return;
            }
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiJavaCodeReferenceElement)) {
                return;
            }
            final PsiJavaCodeReferenceElement referenceElement =
                    (PsiJavaCodeReferenceElement) parent;
            final PsiElement target = referenceElement.resolve();
            if (!(target instanceof PsiClass)) {
                return;
            }
            final PsiClass aClass = (PsiClass) target;
            ImportUtils.addImportIfNeeded((PsiJavaFile) containingFile, aClass);
            element.delete();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessarilyQualifiedInnerClassAccessVisitor();
    }

    private static class UnnecessarilyQualifiedInnerClassAccessVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            final PsiElement qualifier = reference.getQualifier();
            if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
                return;
            }
            if (isInImportOrPackage(reference)) {
                return;
            }
            final PsiJavaCodeReferenceElement referenceElement =
                    (PsiJavaCodeReferenceElement) qualifier;
            final PsiReferenceParameterList parameterList =
                    referenceElement.getParameterList();
            if (parameterList != null &&
                    parameterList.getTypeParameterElements().length > 0) {
                return;
            }
            final PsiElement qualifierTarget = referenceElement.resolve();
            if (!(qualifierTarget instanceof PsiClass)) {
                return;
            }
            final PsiElement target = reference.resolve();
            if (!(target instanceof PsiClass)) {
                return;
            }
            final PsiClass aClass = (PsiClass) target;
            final PsiClass containingClass = aClass.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!containingClass.equals(qualifierTarget)) {
                return;
            }
            final String shortName = aClass.getName();
            if (!isReferenceToTarget(shortName, aClass, reference)) {
                return;
            }
            registerError(qualifier, aClass);
        }

        @Override
        public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            visitReferenceElement(expression);
        }

        private static boolean isReferenceToTarget(
                String referenceText, PsiClass target, PsiElement context) {
            final PsiManager manager = target.getManager();
            final JavaPsiFacade facade =
                    JavaPsiFacade.getInstance(manager.getProject());
            final PsiResolveHelper resolveHelper = facade.getResolveHelper();
            final PsiClass referencedClass =
                    resolveHelper.resolveReferencedClass(referenceText,
                            context);
            if (referencedClass == null) {
                return true;
            }
            return manager.areElementsEquivalent(target, referencedClass);
        }

        private static boolean isInImportOrPackage(PsiElement element) {
            while (element instanceof PsiJavaCodeReferenceElement) {
                element = element.getParent();
                if (element instanceof PsiImportStatementBase ||
                        element instanceof PsiPackageStatement ||
                        element instanceof PsiImportStaticReferenceElement) {
                    return true;
                }
            }
            return false;
        }
    }
}
