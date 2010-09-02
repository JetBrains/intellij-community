/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessarilyQualifiedStaticUsageInspection
        extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreStaticFieldAccesses = false;

    /** @noinspection PublicField*/
    public boolean m_ignoreStaticMethodCalls = false;

    /** @noinspection PublicField*/
    public boolean m_ignoreStaticAccessFromStaticContext = false;

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessarily.qualified.static.usage.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
         final PsiJavaCodeReferenceElement element =
        (PsiJavaCodeReferenceElement)infos[0];
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression) {
            return InspectionGadgetsBundle.message(
                    "unnecessarily.qualified.static.usage.problem.descriptor");
        } else {
            return InspectionGadgetsBundle.message(
                    "unnecessarily.qualified.static.usage.problem.descriptor1");
        }
    }

    @Override
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unnecessarily.qualified.static.usage.ignore.field.option"),
                "m_ignoreStaticFieldAccesses");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unnecessarily.qualified.static.usage.ignore.method.option"),
                "m_ignoreStaticMethodCalls");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "only.report.qualified.static.usages.option"),
                "m_ignoreStaticAccessFromStaticContext");
        return optionsPanel;
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessarilyQualifiedStaticUsageFix();
    }

    private static class UnnecessarilyQualifiedStaticUsageFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.qualifier.for.this.remove.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement element = descriptor.getPsiElement();
            if (element instanceof PsiJavaCodeReferenceElement) {
                final PsiJavaCodeReferenceElement reference =
                        (PsiJavaCodeReferenceElement) element;
                final PsiElement qualifier = reference.getQualifier();
                if (qualifier == null) {
                    return;
                }
                qualifier.delete();
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessarilyQualifiedStaticUsageVisitor();
    }

    private class UnnecessarilyQualifiedStaticUsageVisitor
            extends BaseInspectionVisitor {

        @Override public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            if (!isUnnecessarilyQualifiedAccess(reference)) {
                return;
            }
            registerError(reference, reference);
        }

        @Override public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            visitReferenceElement(expression);
        }

        private boolean isUnnecessarilyQualifiedAccess(
                @NotNull PsiJavaCodeReferenceElement referenceElement) {
            final PsiElement parent = referenceElement.getParent();
            if (parent instanceof PsiJavaCodeReferenceElement ||
                    parent instanceof PsiImportStatementBase) {
                return false;
            }
            final PsiElement qualifierElement =
                    referenceElement.getQualifier();
            if(!(qualifierElement instanceof PsiJavaCodeReferenceElement)){
                return false;
            }
            final PsiReferenceParameterList parameterList =
                    referenceElement.getParameterList();
            if (parameterList != null) {
                final PsiTypeElement[] typeParameterElements =
                        parameterList.getTypeParameterElements();
                if (typeParameterElements.length > 0) {
                    return false;
                }
            }
            final PsiElement target = referenceElement.resolve();
            if ((!(target instanceof PsiField) || m_ignoreStaticFieldAccesses) &&
                    (!(target instanceof PsiMethod) || m_ignoreStaticMethodCalls) &&
                    !(target instanceof PsiClass)) {
                return false;
            }
            if (m_ignoreStaticAccessFromStaticContext) {
                final PsiMember containingMember =
                        PsiTreeUtil.getParentOfType(referenceElement,
                                PsiMember.class);
                if (containingMember != null &&
                        !containingMember.hasModifierProperty(
                                PsiModifier.STATIC)) {
                    return false;
                }
            }
            final String referenceName = referenceElement.getReferenceName();
            if(referenceName == null) {
                return false;
            }
            final PsiReference reference = (PsiReference)qualifierElement;
            final PsiElement resolvedQualifier = reference.resolve();
            if (!(resolvedQualifier instanceof PsiClass)) {
                return false;
            }
            final PsiClass qualifyingClass = (PsiClass)resolvedQualifier;
            final Project project = referenceElement.getProject();
            final JavaPsiFacade manager = JavaPsiFacade.getInstance(project);
            final PsiResolveHelper resolveHelper = manager.getResolveHelper();
            final PsiMember member = (PsiMember) target;
            final PsiClass containingClass;
            if (target instanceof PsiField) {
                final PsiVariable variable =
                        resolveHelper.resolveReferencedVariable(referenceName,
                                referenceElement);
                if (variable == null || !variable.equals(member)) {
                    return false;
                }
                final PsiMember memberVariable = (PsiMember)variable;
                containingClass = memberVariable.getContainingClass();
            } else if (target instanceof PsiClass) {
                final PsiClass aClass =
                        resolveHelper.resolveReferencedClass(referenceName,
                                referenceElement);
                if (aClass == null || !aClass.equals(member)) {
                    return false;
                }
                containingClass = aClass.getContainingClass();
            } else {
                return isMethodAccessibleWithoutQualifier(referenceElement,
                        qualifyingClass);
            }
            return resolvedQualifier.equals(containingClass);
        }

        private boolean isMethodAccessibleWithoutQualifier(
                PsiJavaCodeReferenceElement referenceElement,
                PsiClass qualifyingClass) {
            final String referenceName = referenceElement.getReferenceName();
            if (referenceName == null) {
                return false;
            }
            PsiClass containingClass =
                    ClassUtils.getContainingClass(referenceElement);
            while (containingClass != null) {
                final PsiMethod[] methods =
                        containingClass.findMethodsByName(referenceName,
                                true);
                for(final PsiMethod method : methods) {
                    final String name = method.getName();
                    if(referenceName.equals(name)) {
                        return containingClass.equals(qualifyingClass);
                    }
                }
                containingClass =
                        ClassUtils.getContainingClass(containingClass);
            }
            return false;
        }
    }
}