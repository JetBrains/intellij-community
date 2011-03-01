/*
 * Copyright 2006-2011 Bas Leijdekkers
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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UnqualifiedFieldAccessInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unqualified.field.access.display.name");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnqualifiedFieldAccessVisitor();
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unqualified.field.access.problem.descriptor");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnqualifiedFieldAccessFix();
    }

    private static class UnqualifiedFieldAccessFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "add.this.qualifier.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            if (expression.getQualifierExpression() != null) {
                return;
            }
            final PsiField field = (PsiField)expression.resolve();
            if (field == null) {
                return;
            }
            final PsiClass fieldClass = field.getContainingClass();
            if (fieldClass == null) {
                return;
            }
            PsiClass containingClass =
                    ClassUtils.getContainingClass(expression);
            @NonNls final String newExpression;
            if (InheritanceUtil.isInheritorOrSelf(containingClass, fieldClass,
                    true)) {
                newExpression = "this." + expression.getText();
            } else {
                containingClass =
                        ClassUtils.getContainingClass(containingClass);
                if (containingClass == null) {
                    return;
                }
                while (!InheritanceUtil.isInheritorOrSelf(containingClass,
                        fieldClass, true)) {
                    containingClass =
                            ClassUtils.getContainingClass(containingClass);
                    if (containingClass == null) {
                        return;
                    }
                }
                newExpression = containingClass.getQualifiedName() + ".this." +
                        expression.getText();
            }
            replaceExpressionAndShorten(expression, newExpression);
        }
    }

    private static class UnqualifiedFieldAccessVisitor
            extends BaseInspectionVisitor {

        @Override public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiExpression qualifierExpression =
                    expression.getQualifierExpression();
            if (qualifierExpression != null) {
                return;
            }
            final PsiReferenceParameterList parameterList =
                    expression.getParameterList();
            if (parameterList == null) {
                return;
            }
            if (parameterList.getTypeArguments().length > 0) {
                // optimization: reference with type arguments are
                // definitely not references to fields.
                return;
            }
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField) element;
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            registerError(expression);
        }
    }
}