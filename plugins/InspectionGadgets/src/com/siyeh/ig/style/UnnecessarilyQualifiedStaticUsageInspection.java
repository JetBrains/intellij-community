/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class UnnecessarilyQualifiedStaticUsageInspection
        extends ExpressionInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreStaticFieldAccesses = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticMethodCalls = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticAccessFromStaticContext = false;

    private final UnnecessarilyQualifiedStaticCallFix fix =
            new UnnecessarilyQualifiedStaticCallFix();

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessarily.qualified.static.usage.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiElement parent = location.getParent();
        if (parent instanceof PsiMethodCallExpression) {
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
        return new UnnecessarilyQualifiedStaticCallVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessarilyQualifiedStaticCallFix
            extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.qualifier.for.this.remove.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final String newExpression = expression.getReferenceName();
            replaceExpression(expression, newExpression);
        }
    }

    private class UnnecessarilyQualifiedStaticCallVisitor
            extends BaseInspectionVisitor {

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
            registerError(methodExpression);
        }

        public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (m_ignoreStaticFieldAccesses) {
                return;
            }
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiField)) {
                return;
            }
            if (!isUnnecessarilyQualifiedFieldAccess(expression)) {
                return;
            }
            registerError(expression);
        }

        private boolean isUnnecessarilyQualifiedFieldAccess(
                PsiReferenceExpression expression) {
            final PsiExpression qualifierExpression =
                    expression.getQualifierExpression();
            if(!(qualifierExpression instanceof PsiJavaCodeReferenceElement)){
                return false;
            }
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiField)) {
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
            final String referenceName = expression.getReferenceName();
            if(referenceName == null) {
                return false;
            }
            final PsiManager manager = expression.getManager();
            final PsiResolveHelper resolveHelper = manager.getResolveHelper();
            final PsiVariable variable =
                    resolveHelper.resolveReferencedVariable(referenceName,
                            expression);
            if (variable == null || !variable.equals(member)) {
                return false;
            }
            final PsiElement qualifierElement =
                    ((PsiReference) qualifierExpression).resolve();
            if (!(qualifierElement instanceof PsiClass)) {
                return false;
            }
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(expression);
            return qualifierElement.equals(containingClass);
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
            final PsiElement qualifierElement =
                    ((PsiReference) qualifierExpression).resolve();
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