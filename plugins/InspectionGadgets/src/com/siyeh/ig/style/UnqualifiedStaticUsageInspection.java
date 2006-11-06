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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class UnqualifiedStaticUsageInspection extends ExpressionInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreStaticFieldAccesses = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticMethodCalls = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticAccessFromStaticContext = false;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unqualified.static.usage.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        if (infos[0] instanceof PsiMethodCallExpression) {
            return InspectionGadgetsBundle.message(
                    "unqualified.static.usage.problem.descriptor");
        } else {
            return InspectionGadgetsBundle.message(
                    "unqualified.static.usage.problem.descriptor1");
        }
    }

    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unqualified.static.usage.ignore.field.option"),
                "m_ignoreStaticFieldAccesses");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unqualified.static.usage.ignore.method.option"),
                "m_ignoreStaticMethodCalls");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unqualified,static.usage.only.report.static.usages.option"),
                "m_ignoreStaticAccessFromStaticContext");
        return optionsPanel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnqualifiedStaticCallVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        if (location.getParent() instanceof PsiMethodCallExpression) {
            return new UnqualifiedStaticAccessFix(false);
        } else {
            return new UnqualifiedStaticAccessFix(true);
        }
    }

    private static class UnqualifiedStaticAccessFix
            extends InspectionGadgetsFix {

        private boolean m_fixField;

        UnqualifiedStaticAccessFix(boolean fixField) {
            super();
            m_fixField = fixField;
        }

        public String getName() {
            if (m_fixField) {
                return InspectionGadgetsBundle.message(
                        "unqualified.static.usage.qualify.field.quickfix");
            } else {
                return InspectionGadgetsBundle.message(
                        "unqualified.static.usage.qualify.method.quickfix");
            }
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final PsiMember member = (PsiMember) expression.resolve();
            assert member != null;
            final PsiClass containingClass = member.getContainingClass();
            assert containingClass != null;
            final String className = containingClass.getName();
            final String text = expression.getText();
            replaceExpression(expression, className + '.' + text);
        }
    }

    private class UnqualifiedStaticCallVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (m_ignoreStaticMethodCalls) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if (!isUnqualifiedStaticAccess(methodExpression)) {
                return;
            }
            registerError(methodExpression, expression);
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
            final PsiField field = (PsiField) element;
            if(field.hasModifierProperty(PsiModifier.FINAL) &&
                    PsiUtil.isOnAssignmentLeftHand(expression)) {
                return;
            }
            if (!isUnqualifiedStaticAccess(expression)) {
                return;
            }
            registerError(expression, expression);
        }

        private boolean isUnqualifiedStaticAccess(
                PsiReferenceExpression expression) {
            if (m_ignoreStaticAccessFromStaticContext) {
                final PsiMember member =
                        PsiTreeUtil.getParentOfType(expression,
                                PsiMember.class);
                if (member != null &&
                        member.hasModifierProperty(PsiModifier.STATIC)) {
                    return false;
                }
            }
            final PsiExpression qualifierExpression =
                    expression.getQualifierExpression();
            if(qualifierExpression != null){
                return false;
            }
            final JavaResolveResult resolveResult =
                    expression.advancedResolve(false);
            final PsiElement currentFileResolveScope =
                    resolveResult.getCurrentFileResolveScope();
            if (currentFileResolveScope instanceof PsiImportStaticStatement) {
                return false;
            }
            final PsiElement element = resolveResult.getElement();
            if (!(element instanceof PsiField) &&
                    !(element instanceof PsiMethod)) {
                return false;
            }
            final PsiMember member = (PsiMember) element;
	        if (member instanceof PsiEnumConstant &&
			        expression.getParent() instanceof PsiSwitchLabelStatement) {
		        return false;
	        }
            return member.hasModifierProperty(PsiModifier.STATIC);
        }
    }
}