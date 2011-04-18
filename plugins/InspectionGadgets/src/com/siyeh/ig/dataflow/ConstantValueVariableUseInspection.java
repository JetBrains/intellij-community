/*
 * Copyright 2008-2009 Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConstantValueVariableUseInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "constant.value.variable.use.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "constant.value.variable.use.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiExpression expression = (PsiExpression) infos[0];
        return new ReplaceReferenceWithExpressionFix(expression);
    }

    private static class ReplaceReferenceWithExpressionFix
            extends InspectionGadgetsFix {
      private final SmartPsiElementPointer<PsiExpression> expression;
      private final String myText;

      ReplaceReferenceWithExpressionFix(
                PsiExpression expression) {
          this.expression = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
        myText = expression.getText();
      }


        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "replace.reference.with.expression.quickfix",
                    myText);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();

          PsiExpression exp  = expression.getElement();
          if (exp == null) return;
          element.replace(exp );
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
            final PsiStatement body = statement.getThenBranch();
            checkCondition(condition, body);
        }

        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiExpression condition = statement.getCondition();
            final PsiStatement body = statement.getBody();
            checkCondition(condition, body);
        }

        @Override
        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiExpression condition = statement.getCondition();
            final PsiStatement body = statement.getBody();
            checkCondition(condition, body);
        }

        private boolean checkCondition(@Nullable PsiExpression condition,
                                       @Nullable PsiStatement body) {
            if (body == null) {
                return false;
            }
            if (!(condition instanceof PsiBinaryExpression)) {
                return false;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) condition;
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (JavaTokenType.ANDAND == tokenType) {
                return checkCondition(lhs, body) ||
                        checkCondition(rhs, body);
            }
            if (JavaTokenType.EQEQ != tokenType) {
                return false;
            }
            if (rhs == null) {
                return false;
            }
            if (PsiUtil.isConstantExpression(lhs)) {
                return checkConstantValueVariableUse(rhs, lhs, body);
            } else if (PsiUtil.isConstantExpression(rhs)) {
                return checkConstantValueVariableUse(lhs, rhs, body);
            }
            return false;
        }

        private boolean checkConstantValueVariableUse(
                @Nullable PsiExpression expression,
                @NotNull PsiExpression constantExpression,
                @NotNull PsiElement body) {
            final PsiType constantType = constantExpression.getType();
            if (PsiType.DOUBLE.equals(constantType)) {
                final Object result = ExpressionUtils.computeConstantExpression(
                        constantExpression, false);
                if (Double.valueOf(0.0).equals(result) ||
                        Double.valueOf(-0.0).equals(result)) {
                    return false;
                }
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) expression;
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return false;
            }
            if (target instanceof PsiField) {
                return false;
            }
            final PsiVariable variable = (PsiVariable) target;
            final VariableReadVisitor visitor =
                    new VariableReadVisitor(variable);
            body.accept(visitor);
            if (!visitor.isRead()) {
                return false;
            }
            registerError(visitor.getReference(), constantExpression);
            return true;
        }
    }

    private static class VariableReadVisitor
            extends JavaRecursiveElementVisitor {

        @NotNull
        private final PsiVariable variable;
        private boolean read = false;
        private boolean written = false;
        private PsiReferenceExpression reference = null;

        VariableReadVisitor(@NotNull PsiVariable variable) {
            this.variable = variable;
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
            if (read || written) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression assignment) {
            if (read || written) {
                return;
            }
            super.visitAssignmentExpression(assignment);
            final PsiExpression lhs = assignment.getLExpression();
            if (lhs instanceof PsiReferenceExpression) {
                PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) lhs;
                final PsiElement target = referenceExpression.resolve();
                if (variable.equals(target)) {
                    written = true;
                    return;
                }
            }
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

        @Override
        public void visitPrefixExpression(
                @NotNull PsiPrefixExpression prefixExpression) {
            if (read || written) {
                return;
            }
            super.visitPrefixExpression(prefixExpression);
            final PsiJavaToken operationSign =
                    prefixExpression.getOperationSign();
            final IElementType tokenType = operationSign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = prefixExpression.getOperand();
            if (!(operand instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) operand;
            final PsiElement target = referenceExpression.resolve();
            if (!variable.equals(target)) {
                return;
            }
            written = true;
        }

        @Override
        public void visitPostfixExpression(
                @NotNull PsiPostfixExpression postfixExpression) {
            if (read || written) {
                return;
            }
            super.visitPostfixExpression(postfixExpression);
            final PsiJavaToken operationSign =
                    postfixExpression.getOperationSign();
            final IElementType tokenType = operationSign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = postfixExpression.getOperand();
            if (!(operand instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) operand;
            final PsiElement target = referenceExpression.resolve();
            if (!variable.equals(target)) {
                return;
            }
            written = true;
        }

        @Override
        public void visitVariable(@NotNull PsiVariable variable) {
            if (read || written) {
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

        @Override
        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call) {
            if (read || written) {
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiExpressionList argumentList = call.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            for (final PsiExpression argument : arguments) {
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

        @Override
        public void visitNewExpression(
                @NotNull PsiNewExpression newExpression) {
            if (read || written) {
                return;
            }
            super.visitNewExpression(newExpression);
            final PsiExpressionList argumentList =
                    newExpression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            for (final PsiExpression argument : arguments) {
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

        @Override
        public void visitArrayInitializerExpression(
                PsiArrayInitializerExpression expression) {
            if (read || written) {
                return;
            }
            super.visitArrayInitializerExpression(expression);
            final PsiExpression[] arguments = expression.getInitializers();
            for (final PsiExpression argument : arguments) {
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

        @Override
        public void visitReturnStatement(
                @NotNull PsiReturnStatement returnStatement) {
            if (read || written) {
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
            if (read || written) {
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
