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
package com.siyeh.ig.dataflow;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ConstantValueVariableUseInspection extends BaseInspection {

    @Override @NotNull
    public String getDisplayName() {
        return "Use of variable whose value is known to be constant";
    }

    @Override @NotNull
    protected String buildErrorString(Object... infos) {
        return "Value of <code>#ref</code> is known to be constant";
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiExpression expression = (PsiExpression) infos[0];
        return new ReplaceReferenceWithExpressionFix(expression);
    }

    private static class ReplaceReferenceWithExpressionFix
            extends InspectionGadgetsFix {

        private final PsiExpression expression;

        public ReplaceReferenceWithExpressionFix(
                PsiExpression expression) {
            this.expression = expression;
        }


        @NotNull
        public String getName() {
            return "Replace with '" + expression.getText() + "'";
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            element.replace(expression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ConstantValueVariableUseVisitor();
    }

    private static class ConstantValueVariableUseVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if (!(condition instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) condition;
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            if (JavaTokenType.EQEQ != tokenType) {
                return;
            }
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return;
            }
            final PsiExpression lhs = binaryExpression.getLOperand();
            if (PsiUtil.isConstantExpression(lhs)) {
                checkConstantValueVariableUse(statement, rhs, lhs);
            } else if (PsiUtil.isConstantExpression(rhs)) {
                checkConstantValueVariableUse(statement, lhs, rhs);
            }
        }

        private void checkConstantValueVariableUse(
                PsiIfStatement statement,
                PsiExpression expression,
                PsiExpression constantExpression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) expression;
            if (referenceExpression.getQualifierExpression() != null) {
                return;
            }
            final PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return;
            }
            final PsiVariable variable = (PsiVariable) target;
            final VariableUsedVisitor visitor = new VariableUsedVisitor(
                    variable);
            thenBranch.accept(visitor);
            if (!visitor.isUsed()) {
                return;
            }
            registerError(visitor.getUse(), constantExpression);
        }
    }

    // cannot rename this class VarialbeReadVisitor
    private static class VariableUsedVisitor
            extends JavaRecursiveElementVisitor {

        @NotNull private final PsiVariable variable;
        private boolean used = false;
        private PsiReferenceExpression use;

        public VariableUsedVisitor(@NotNull PsiVariable variable){
            super();
            this.variable = variable;
        }

        @Override public void visitElement(@NotNull PsiElement element){
            if(!used){
                super.visitElement(element);
            }
        }

        @Override public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression){
            if(used){
                return;
            }
            super.visitReferenceExpression(expression);
            final PsiElement referent = expression.resolve();
            if(referent == null){
                return;
            }
            if(referent.equals(variable)){
                use = expression;
                used = true;
            }
        }

        public boolean isUsed(){
            return used;
        }

        public PsiReferenceExpression getUse() {
            return use;
        }
    }
}
