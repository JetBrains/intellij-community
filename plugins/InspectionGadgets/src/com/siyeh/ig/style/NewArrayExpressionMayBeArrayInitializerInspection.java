/*
 * Copyright 2008 Bas Leijdekkers
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

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewArrayExpressionMayBeArrayInitializerInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "new.array.expression.may.be.array.initializer.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return "New array expression may be array initializer";
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new NewArrayExpressionMayBeArrayInitializerFix();
    }

    private static class NewArrayExpressionMayBeArrayInitializerFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return "Replace with array initializer";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiNewExpression)) {
                return;
            }
            final PsiNewExpression newExpression = (PsiNewExpression) element;
            final PsiArrayInitializerExpression arrayInitializer =
                    newExpression.getArrayInitializer();
            if (arrayInitializer == null) {
                return;
            }
            newExpression.replace(arrayInitializer);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NewArrayExpressionMayBeArrayInitializerVisitor();
    }

    private static class NewArrayExpressionMayBeArrayInitializerVisitor
            extends BaseInspectionVisitor {

        public void visitArrayInitializerExpression(
                PsiArrayInitializerExpression expression) {
            super.visitArrayInitializerExpression(expression);
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiNewExpression)) {
                return;
            }
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiVariable)) {
                return;
            }
            registerError(parent);
        }
    }
}