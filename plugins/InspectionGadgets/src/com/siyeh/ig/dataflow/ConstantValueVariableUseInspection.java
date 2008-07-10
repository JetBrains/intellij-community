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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ConstantValueVariableUseInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "constant.value.variable.use.display.name");
    }

    @Override @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "constant.value.variable.use.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiExpression expression = (PsiExpression)infos[0];
        return new ReplaceReferenceWithExpressionFix(expression);
    }

    private static class ReplaceReferenceWithExpressionFix
            extends InspectionGadgetsFix {

        private final PsiExpression expression;

        ReplaceReferenceWithExpressionFix(
                PsiExpression expression) {
            this.expression = expression;
        }


        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "replace.reference.with.expression.quickfix",
                    expression.getText());
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
                    (PsiBinaryExpression)condition;
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
                    (PsiReferenceExpression)expression;
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
            final PsiVariable variable = (PsiVariable)target;
            final VariableReadVisitor visitor = new VariableReadVisitor(
                    variable);
            thenBranch.accept(visitor);
            if (!visitor.isRead()) {
                return;
            }
            registerError(visitor.getReference(), constantExpression);
        }
    }

    private static class VariableReadVisitor
            extends JavaRecursiveElementVisitor {

        @NotNull
        private final PsiVariable variable;
        private boolean read = false;
        private PsiReferenceExpression reference = null;

        VariableReadVisitor(@NotNull PsiVariable variable) {
            this.variable = variable;
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
            if (read) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression assignment) {
            if (read) {
                return;
            }
            super.visitAssignmentExpression(assignment);
            final PsiExpression rhs = assignment.getRExpression();
            if (rhs == null) {
                return;
            }
            final VariableUsedVisitor visitor =
                    new VariableUsedVisitor(variable);
            rhs.accept(visitor);
            read = visitor.isUsed();
            reference = visitor.getReference();
        }

        @Override public void visitVariable(@NotNull PsiVariable variable){
            if(read){
                return;
            }
            super.visitVariable(variable);
            final PsiExpression initalizer = variable.getInitializer();
            if (initalizer == null) {
                return;
            }
            final VariableUsedVisitor visitor =
                    new VariableUsedVisitor(variable);
            initalizer.accept(visitor);
            read = visitor.isUsed();
            reference = visitor.getReference();
        }

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            if(read){
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiExpressionList argumentList = call.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            for(final PsiExpression argument : arguments){
                final VariableUsedVisitor visitor =
                        new VariableUsedVisitor(variable);
                argument.accept(visitor);
                if (visitor.isUsed()) {
                    read = true;
                    reference = visitor.getReference();
                    return;
                }
            }
        }

        @Override public void visitNewExpression(
                @NotNull PsiNewExpression newExpression){
            if(read){
                return;
            }
            super.visitNewExpression(newExpression);
            final PsiExpressionList argumentList =
                    newExpression.getArgumentList();
            if(argumentList == null){
                return;
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            for(final PsiExpression argument : arguments){
                final VariableUsedVisitor visitor =
                        new VariableUsedVisitor(variable);
                argument.accept(visitor);
                if (visitor.isUsed()) {
                    read = true;
                    reference = visitor.getReference();
                    return;
                }
            }
        }

        @Override public void visitArrayInitializerExpression(
                PsiArrayInitializerExpression expression){
            if(read){
                return;
            }
            super.visitArrayInitializerExpression(expression);
            final PsiExpression[] arguments = expression.getInitializers();
            for(final PsiExpression argument : arguments){
                final VariableUsedVisitor visitor =
                        new VariableUsedVisitor(variable);
                argument.accept(visitor);
                if (visitor.isUsed()) {
                    read = true;
                    reference = visitor.getReference();
                    return;
                }
            }
        }

        @Override public void visitReturnStatement(
                @NotNull PsiReturnStatement returnStatement) {
            if(read){
                return;
            }
            super.visitReturnStatement(returnStatement);
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if (returnValue == null) {
                return;
            }
            final VariableUsedVisitor visitor =
                    new VariableUsedVisitor(variable);
            returnValue.accept(visitor);
            read = visitor.isUsed();
            reference = visitor.getReference();
        }

        /**
         * check if variable is used in nested/inner class.
         */
        @Override
        public void visitClass(PsiClass aClass) {
            if (read) {
                return;
            }
            super.visitClass(aClass);
            final VariableUsedVisitor visitor =
                    new VariableUsedVisitor(variable);
            aClass.accept(visitor);
            read = visitor.isUsed();
            reference = visitor.getReference();
        }

        public boolean isRead() {
            return read;
        }

        public PsiReferenceExpression getReference() {
            return reference;
        }
    }

    private static class VariableUsedVisitor
            extends JavaRecursiveElementVisitor {

        private final PsiVariable variable;
        private boolean used = false;
        private PsiReferenceExpression reference = null;

        VariableUsedVisitor(PsiVariable variable) {
            this.variable = variable;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (used) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression) {
            if (used) {
                return;
            }
            super.visitReferenceExpression(expression);
            final PsiElement referent = expression.resolve();
            if (referent == null) {
                return;
            }
            if (referent.equals(variable)) {
                reference = expression;
                used = true;
            }
        }

        public boolean isUsed() {
            return used;
        }

        public PsiReferenceExpression getReference() {
            return reference;
        }
    }
}
