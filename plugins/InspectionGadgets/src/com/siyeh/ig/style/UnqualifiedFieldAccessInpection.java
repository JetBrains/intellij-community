/*
 * Copyright 2006 Bas Leijdekkers
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class UnqualifiedFieldAccessInpection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unqualified.field.access.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnqualifiedFieldAccessVisitor();
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unqualified.field.access.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new UnqualifiedFieldAccessFix();
    }

    private static class UnqualifiedFieldAccessFix
            extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "add.this.qualifier.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final PsiField field = (PsiField)expression.resolve();
            if (field == null) {
                return;
            }
            final PsiClass containingClass = field.getContainingClass();
            final PsiClass parentClass =
                    PsiTreeUtil.getParentOfType(expression, PsiClass.class);
            @NonNls final String newExpression;
            if (!containingClass.equals(parentClass)) {
                newExpression = containingClass.getQualifiedName() + ".this." +
                        expression.getText();
            } else {
                newExpression = "this." + expression.getText();
            }
            replaceExpressionAndShorten(expression, newExpression);
        }
    }

    private static class UnqualifiedFieldAccessVisitor
            extends BaseInspectionVisitor {

        public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement parent = 
                    expression.getParent();
            if (parent instanceof PsiReferenceExpression ||
                    parent instanceof PsiCallExpression) {
                // optimization
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
            final PsiExpression qualifierExpression =
                    expression.getQualifierExpression();
            if (qualifierExpression != null) {
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