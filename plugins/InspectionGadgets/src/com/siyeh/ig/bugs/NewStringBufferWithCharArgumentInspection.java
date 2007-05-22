/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewStringBufferWithCharArgumentInspection extends BaseInspection {

    @Nls
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "new.string.buffer.with.char.argument.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "new.string.buffer.with.char.argument.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final PsiNewExpression newExpression =
                (PsiNewExpression) location.getParent();
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
            return null;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression argument = arguments[0];
        if (!(argument instanceof PsiLiteralExpression)) {
            return null;
        }
        return new NewStringBufferWithCharArgumentFix();
    }

    private static class NewStringBufferWithCharArgumentFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "new.string.buffer.with.char.argument.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiNewExpression newExpression =
                    (PsiNewExpression) element.getParent();
            final PsiExpressionList argumentList =
                    newExpression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            final String text = argument.getText();
            final String newArgument =
                    '"' + text.substring(1, text.length() - 1) + '"';
            replaceExpression(argument, newArgument);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringBufferWithCharArgumentVisitor();
    }

    private static class StringBufferWithCharArgumentVisitor
            extends BaseInspectionVisitor {

        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            final PsiType type = argument.getType();
            if (!PsiType.CHAR.equals(type)) {
                return;
            }
            final PsiMethod constructor = expression.resolveConstructor();
            if (constructor == null) {
                return;
            }
            final PsiClass aClass = constructor.getContainingClass();
            if (!ClassUtils.isSubclass(aClass,
                    "java.lang.AbstractStringBuilder")) {
                return;
            }
            registerNewExpressionError(expression);
        }
    }
}