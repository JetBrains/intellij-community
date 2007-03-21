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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class UnnecessarilyQualifiedStaticUsageInspection
        extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreStaticFieldAccesses = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticMethodCalls = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticAccessFromStaticContext = false;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessarily.qualified.static.usage.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final boolean methodCall = ((Boolean)infos[0]).booleanValue();
        if (methodCall) {
            return InspectionGadgetsBundle.message(
                    "unnecessarily.qualified.static.usage.problem.descriptor");
        } else {
            return InspectionGadgetsBundle.message(
                    "unnecessarily.qualified.static.usage.problem.descriptor1");
        }
    }

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

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessarilyQualifiedStaticUsageVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new UnnecessarilyQualifiedStaticUsageFix();
    }

    private static class UnnecessarilyQualifiedStaticUsageFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.qualifier.for.this.remove.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement element = descriptor.getPsiElement();
            if (element instanceof PsiReferenceExpression) {
                final PsiReferenceExpression expression =
                        (PsiReferenceExpression) element;
                final String newExpression = expression.getReferenceName();
                if (newExpression == null) {
                    return;
                }
                replaceExpression(expression, newExpression);
            } else {
                final PsiManager manager = element.getManager();
                final CodeStyleManager styleManager =
                        manager.getCodeStyleManager();
                styleManager.shortenClassReferences(element);
            }
        }
    }

    private class UnnecessarilyQualifiedStaticUsageVisitor
            extends BaseInspectionVisitor {

        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiJavaCodeReferenceElement classReference =
                    expression.getClassReference();
            if (classReference == null) {
                return;
            }
            if (!isUnnecessarilyQualifiedAccess(classReference)) {
                return;
            }
            registerNewExpressionError(expression, Boolean.FALSE);
        }

        public void visitReferenceList(PsiReferenceList list) {
            super.visitReferenceList(list);
            final PsiJavaCodeReferenceElement[] referenceElements =
                    list.getReferenceElements();
            for (PsiJavaCodeReferenceElement referenceElement :
                    referenceElements) {
                if (isUnnecessarilyQualifiedAccess(referenceElement)) {
                    registerError(referenceElement, Boolean.FALSE);
                }
            }
        }

        public void visitTypeElement(PsiTypeElement type) {
            super.visitTypeElement(type);
            final PsiJavaCodeReferenceElement typeReference =
                    type.getInnermostComponentReferenceElement();
            if (typeReference == null) {
                return;
            }
            if (!isUnnecessarilyQualifiedAccess(typeReference)) {
                return;
            }
            registerError(typeReference, Boolean.FALSE);
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (m_ignoreStaticMethodCalls) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if (!isUnnecessarilyQualifiedMethodCall(methodExpression)) {
                return;
            }
            registerError(methodExpression, Boolean.TRUE);
        }

        public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (m_ignoreStaticFieldAccesses) {
                return;
            }
            if (!isUnnecessarilyQualifiedAccess(expression)) {
                return;
            }
            registerError(expression, Boolean.FALSE);
        }

        private boolean isUnnecessarilyQualifiedAccess(
                @NotNull PsiJavaCodeReferenceElement referenceElement) {
            final PsiElement qualifierElement =
                    referenceElement.getQualifier();
            if(!(qualifierElement instanceof PsiJavaCodeReferenceElement)){
                return false;
            }
            final PsiElement target = referenceElement.resolve();
            if (!(target instanceof PsiField) &&
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
            final PsiManager manager = referenceElement.getManager();
            final PsiResolveHelper resolveHelper = manager.getResolveHelper();
            final PsiMember member = (PsiMember) target;
            if (target instanceof PsiField) {
                if (!member.hasModifierProperty(PsiModifier.STATIC)) {
                    return false;
                }
                final PsiVariable variable =
                        resolveHelper.resolveReferencedVariable(referenceName,
                                referenceElement);
                if (variable == null || !variable.equals(member)) {
                    return false;
                }
            } else {
                final PsiClass aClass =
                        resolveHelper.resolveReferencedClass(referenceName,
                                referenceElement);
                if (aClass == null || !aClass.equals(member)) {
                    return false;
                }
            }
            final PsiReference reference = (PsiReference)qualifierElement;
            final PsiElement resolvedQualifier =
                    reference.resolve();
            if (!(resolvedQualifier instanceof PsiClass)) {
                return false;
            }
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(referenceElement);
            return resolvedQualifier.equals(containingClass);
        }

        private boolean isUnnecessarilyQualifiedMethodCall(
                PsiReferenceExpression expression) {
            final PsiExpression qualifierExpression =
                    expression.getQualifierExpression();
            if(!(qualifierExpression instanceof PsiJavaCodeReferenceElement)) {
                return false;
            }
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiMethod)) {
                return false;
            }
            final PsiMember member = (PsiMember) element;
            if (!member.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }
            if (m_ignoreStaticAccessFromStaticContext) {
                final PsiMember containingMember =
                        PsiTreeUtil.getParentOfType(expression,
                                PsiMember.class);
                if (containingMember != null &&
                        !containingMember.hasModifierProperty(
                                PsiModifier.STATIC)) {
                    return false;
                }
            }
            final PsiReference reference = (PsiReference)qualifierExpression;
            final PsiElement qualifierElement = reference.resolve();
            if (!(qualifierElement instanceof PsiClass)) {
                return false;
            }
            final String referenceName = expression.getReferenceName();
            if(referenceName == null) {
                return false;
            }
            PsiClass parentClass = ClassUtils.getContainingClass(expression);
            PsiClass containingClass = parentClass;
            while (parentClass != null) {
                containingClass = parentClass;
                final PsiMethod[] methods = containingClass.getAllMethods();
                for(final PsiMethod method : methods) {
                    final String name = method.getName();
                    if(referenceName.equals(name) &&
                            !containingClass.equals(qualifierElement)) {
                        return false;
                    }
                }
                parentClass = ClassUtils.getContainingClass(containingClass);
            }
            return qualifierElement.equals(containingClass);
        }
    }
}