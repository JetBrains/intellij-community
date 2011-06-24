/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UseOfAnotherObjectsPrivateFieldInspection
        extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreSameClass = false;
    @SuppressWarnings({"PublicField"})
    public boolean ignoreEquals = false;

    @Override
    @NotNull
    public String getID(){
        return "AccessingNonPublicFieldOfAnotherObject";
    }

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "accessing.non.public.field.of.another.object.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "accessing.non.public.field.of.another.object.problem.descriptor");
    }

    @Override
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel panel =
                new MultipleCheckboxOptionsPanel(this);
        panel.addCheckbox(InspectionGadgetsBundle.message(
                "ignore.accesses.from.the.same.class"), "ignoreSameClass");
        panel.addCheckbox(InspectionGadgetsBundle.message(
                "ignore.accesses.from.equals.method"), "ignoreEquals");
        return panel;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiField field = (PsiField) infos[0];
        final String propertyName = field.getName();
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) infos[1];
        final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        final PsiClass containingClass = field.getContainingClass();
        if (PsiUtil.isAccessedForReading(referenceExpression)) {
            if (PsiUtil.isAccessedForWriting(referenceExpression)) {
                return null;
            }
            final PsiMethod getter =
                    PropertyUtil.findPropertyGetter(containingClass,
                            propertyName, isStatic, true);
            if (getter == null) {
                return null;
            }
            return new UseOfAnotherObjectsPrivateFieldFix(getter);
        } else if (PsiUtil.isAccessedForWriting(referenceExpression)) {
            final PsiMethod setter =
                    PropertyUtil.findPropertySetter(containingClass,
                            propertyName, isStatic, true);
            if (setter == null) {
                return null;
            }
            return new UseOfAnotherObjectsPrivateFieldFix(setter);
        }
        return null;
    }

    private static class UseOfAnotherObjectsPrivateFieldFix
            extends InspectionGadgetsFix {

        private final PsiMethod method;

        public UseOfAnotherObjectsPrivateFieldFix(PsiMethod method) {
            this.method = method;
        }

        @NotNull
        @Override
        public String getName() {
            return "Replace with call to '" + method.getName() + "'";
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) parent;
            final StringBuilder methodCallText = new StringBuilder();
            final PsiExpression qualifierExpression =
                    referenceExpression.getQualifierExpression();
            if (qualifierExpression != null) {
                methodCallText.append(qualifierExpression.getText());
                methodCallText.append('.');
            }
            methodCallText.append(method.getName());
            if (!PsiUtil.isOnAssignmentLeftHand(referenceExpression)) {
                methodCallText.append("()");
                replaceExpression(referenceExpression,
                        methodCallText.toString());
            } else {
                final PsiAssignmentExpression assignmentExpression =
                        PsiTreeUtil.getParentOfType(referenceExpression,
                                PsiAssignmentExpression.class);
                if (assignmentExpression == null) {
                    return;
                }
                methodCallText.append('(');
                final PsiExpression rhs = assignmentExpression.getRExpression();
                if (rhs != null) {
                    methodCallText.append(rhs.getText());
                }
                methodCallText.append(')');
                replaceExpression(assignmentExpression,
                        methodCallText.toString());
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new UseOfAnotherObjectsPrivateFieldVisitor();
    }

    private class UseOfAnotherObjectsPrivateFieldVisitor
            extends BaseInspectionVisitor{

        @Override public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression){
            super.visitReferenceExpression(expression);
            final PsiExpression qualifier = expression.getQualifierExpression();
            if(qualifier == null || qualifier instanceof PsiThisExpression){
                return;
            }
            if(ignoreEquals) {
                final PsiMethod method =
                        PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
                if (MethodUtils.isEquals(method)) {
                    return;
                }
            }
            final PsiElement referent = expression.resolve();
            if(!(referent instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) referent;
            if (ignoreSameClass) {
                final PsiClass parent =
                        PsiTreeUtil.getParentOfType(expression, PsiClass.class);
                final PsiClass containingClass = field.getContainingClass();
                if (parent != null && parent.equals(containingClass)) {
                    return;
                }
            }
            if(!field.hasModifierProperty(PsiModifier.PRIVATE) &&
                    !field.hasModifierProperty(PsiModifier.PROTECTED)){
                return;
            }
            if(field.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }
            final PsiElement fieldNameElement =
                    expression.getReferenceNameElement();
            if(fieldNameElement == null){
                return;
            }
            registerError(fieldNameElement, field, expression);
        }
    }
}
