/*
 * Copyright 2009 Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class IntLiteralMayBeLongLiteralInspection extends BaseInspection {

    @Override
    @Nls
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "int.literal.may.be.long.literal.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiLiteralExpression literalExpression =
                (PsiLiteralExpression) infos[0];
        final String replacementString = literalExpression.getText() + 'L';
        return InspectionGadgetsBundle.message(
                "int.literal.may.be.long.literal.problem.descriptor",
                replacementString);
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiLiteralExpression literalExpression =
                (PsiLiteralExpression) infos[0];
        final String replacementString = literalExpression.getText() + 'L';
        return new IntLiteralMayBeLongLiteralFix(replacementString);
    }

    private static class IntLiteralMayBeLongLiteralFix
            extends InspectionGadgetsFix {

        private final String replacementString;

        public IntLiteralMayBeLongLiteralFix(String replacementString) {
            this.replacementString = replacementString;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "int.literal.may.be.long.literal.quickfix",
                    replacementString);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiTypeCastExpression)) {
                return;
            }
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression) element;
            replaceExpression(typeCastExpression, replacementString);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IntLiteralMayBeLongLiteralVisitor();
    }

    private static class IntLiteralMayBeLongLiteralVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitLiteralExpression(PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if (PsiType.INT != type) {
                return;
            }
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiTypeCastExpression)) {
                return;
            }
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression) parent;
            final PsiType castType = typeCastExpression.getType();
            if (PsiType.LONG != castType) {
                return;
            }
            registerError(typeCastExpression, expression);
        }
    }
}
