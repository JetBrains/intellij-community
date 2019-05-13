/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyConstantConditionalInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return "Constant conditional expression";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ConstantConditionalExpressionVisitor();
    }

    @Override
    @NotNull
    public String buildErrorString(Object... args) {
        return "'#ref' can be simplified #loc";
    }

    static String calculateReplacementExpression(
            GrConditionalExpression exp) {
        final GrExpression thenExpression = exp.getThenBranch();
        final GrExpression elseExpression = exp.getElseBranch();
        final GrExpression condition = exp.getCondition();
        assert thenExpression != null;
        assert elseExpression != null;
        if (isTrue(condition)) {
            return thenExpression.getText();
        } else {
            return elseExpression.getText();
        }
    }

    @Override
    public GroovyFix buildFix(@NotNull PsiElement location) {
        return new ConstantConditionalFix();
    }

    private static class ConstantConditionalFix extends GroovyFix {

        @Override
        @NotNull
        public String getFamilyName() {
            return "Simplify";
        }

        @Override
        public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final GrConditionalExpression expression =
                    (GrConditionalExpression) descriptor.getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class ConstantConditionalExpressionVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitConditionalExpression(@NotNull GrConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final GrExpression condition = expression.getCondition();
            final GrExpression thenExpression = expression.getThenBranch();
            if (thenExpression == null) {
                return;
            }
            final GrExpression elseExpression = expression.getElseBranch();
            if (elseExpression == null) {
                return;
            }
            if (isFalse(condition) || isTrue(condition)) {
                registerError(expression, expression);
            }
        }
    }

    private static boolean isFalse(GrExpression expression) {
        @NonNls final String text = expression.getText();
        return "false".equals(text);
    }

    private static boolean isTrue(GrExpression expression) {
        @NonNls final String text = expression.getText();
        return "true".equals(text);
    }
}
