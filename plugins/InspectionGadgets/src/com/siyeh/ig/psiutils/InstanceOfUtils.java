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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstanceOfUtils {

    public static boolean hasConflictingInstanceof(
            @NotNull PsiTypeCastExpression expression) {
        PsiType castType = expression.getType();
        PsiExpression operand = expression.getOperand();
        if (!(operand instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression)operand;
        final InstanceofChecker checker = new InstanceofChecker(
                referenceExpression, castType, false);
        PsiElement parent = PsiTreeUtil.getParentOfType(expression,
                PsiBinaryExpression.class, PsiIfStatement.class,
                PsiConditionalExpression.class);
        while (parent != null) {
            parent.accept(checker);
            if (checker.hasAgreeingInstanceof()) {
                return false;
            }
            parent = PsiTreeUtil.getParentOfType(parent,
                    PsiBinaryExpression.class, PsiIfStatement.class,
                    PsiConditionalExpression.class);
        }
        return checker.hasConflictingInstanceof();
    }

    public static boolean hasAgreeingInstanceof(
            @NotNull PsiTypeCastExpression expression) {
        PsiType castType = expression.getType();
        PsiExpression operand = expression.getOperand();
        if (!(operand instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression)operand;
        final InstanceofChecker checker = new InstanceofChecker(
                referenceExpression, castType, false);
        PsiElement parent = PsiTreeUtil.getParentOfType(expression,
                PsiBinaryExpression.class, PsiIfStatement.class,
                PsiConditionalExpression.class);
        while (parent != null) {
            parent.accept(checker);
            if (checker.hasAgreeingInstanceof()) {
                return true;
            }
            parent = PsiTreeUtil.getParentOfType(parent,
                    PsiBinaryExpression.class, PsiIfStatement.class,
                    PsiConditionalExpression.class);
        }
        return false;
    }

    public static class InstanceofChecker extends PsiElementVisitor {

        private final PsiReferenceExpression referenceExpression;
        private final PsiType castType;
        private final boolean strict;
        private boolean inElse = false;
        private boolean conflictingInstanceof = false;
        private boolean agreeingInstanceof = false;


        InstanceofChecker(PsiReferenceExpression referenceExpression,
                          PsiType castType, boolean strict) {
            this.referenceExpression = referenceExpression;
            this.castType = castType;
            this.strict = strict;
        }

        @Override public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            visitExpression(expression);
        }

        @Override public void visitBinaryExpression(PsiBinaryExpression expression) {
            final PsiJavaToken sign =
                    expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType == JavaTokenType.ANDAND) {
                inElse = false;
                final PsiExpression lhs = expression.getLOperand();
                checkExpression(lhs);
                final PsiExpression rhs = expression.getROperand();
                checkExpression(rhs);
            } else if (tokenType == JavaTokenType.OROR) {
                inElse = true;
                final PsiExpression lhs = expression.getLOperand();
                checkExpression(lhs);
            }
        }

        @Override public void visitIfStatement(PsiIfStatement ifStatement) {
            final PsiStatement branch = ifStatement.getElseBranch();
            inElse = branch != null &&
                    PsiTreeUtil.isAncestor(branch, referenceExpression,
                            true);
            if (inElse) {
                if (branch instanceof PsiBlockStatement) {
                    final PsiBlockStatement blockStatement =
                            (PsiBlockStatement)branch;
                    if (isVariableAssignedBeforeReference(blockStatement)) {
                        return;
                    }
                }
            } else {
                final PsiStatement thenBranch = ifStatement.getThenBranch();
                if (thenBranch instanceof PsiBlockStatement) {
                    final PsiBlockStatement blockStatement =
                            (PsiBlockStatement)thenBranch;
                    if (isVariableAssignedBeforeReference(blockStatement)) {
                        return;
                    }
                }
            }
            PsiExpression condition = ifStatement.getCondition();
            condition = PsiUtil.deparenthesizeExpression(condition);
            if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)condition;
                visitBinaryExpression(binaryExpression);
            } else {
                checkExpression(condition);
            }
        }

        @Override public void visitConditionalExpression(
                PsiConditionalExpression expression) {
            final PsiExpression elseExpression =
                    expression.getElseExpression();
            inElse = elseExpression != null &&
                    PsiTreeUtil.isAncestor(elseExpression,
                            referenceExpression, true);
            PsiExpression condition = expression.getCondition();
            condition = PsiUtil.deparenthesizeExpression(condition);
            if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)condition;
                visitBinaryExpression(binaryExpression);
            } else {
                checkExpression(condition);
            }
        }

        private boolean isVariableAssignedBeforeReference(
                @Nullable PsiElement element) {
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return false;
            }
            final PsiVariable variable = (PsiVariable)target;
            return isVariableAssignedAtPoint(variable, element,
                    referenceExpression);
        }

        private static boolean isVariableAssignedAtPoint(
                @NotNull PsiVariable variable, @Nullable PsiElement context,
                PsiElement point) {
            if (context == null) {
                return false;
            }
            final PsiElement directChild =
                    getDirectChildWhichContainsElement(context, point);
            if (directChild == null) {
                return false;
            }
            final PsiElement[] children = context.getChildren();
            for (PsiElement child : children) {
                if (child == directChild) {
                    return isVariableAssignedAtPoint(variable, directChild,
                            point);
                }
                if (VariableAccessUtils.variableIsAssigned(variable,
                        child)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        public static PsiElement getDirectChildWhichContainsElement(
                @NotNull PsiElement ancestor,
                @NotNull PsiElement descendant) {
            if (ancestor == descendant) {
                return null;
            }
            PsiElement child = descendant;
            PsiElement parent = child.getParent();
            while (!parent.equals(ancestor)) {
                child = parent;
                parent = child.getParent();
                if (parent == null) {
                    return null;
                }
            }
            return child;
        }

        private void checkExpression(PsiExpression expression) {
            expression = PsiUtil.deparenthesizeExpression(expression);
            if (inElse) {
                if (expression instanceof PsiPrefixExpression) {
                    final PsiPrefixExpression prefixExpression =
                            (PsiPrefixExpression)expression;
                    final IElementType tokenType =
                            prefixExpression.getOperationTokenType();
                    if (tokenType != JavaTokenType.EXCL) {
                        return;
                    }
                    expression = PsiUtil.deparenthesizeExpression(
                            prefixExpression.getOperand());
                    checkInstanceOfExpression(expression);
                }
            } else {
                checkInstanceOfExpression(expression);
            }
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)expression;
                visitBinaryExpression(binaryExpression);
            }
        }

        private void checkInstanceOfExpression(PsiExpression expression) {
            if (expression instanceof PsiInstanceOfExpression) {
                final PsiInstanceOfExpression instanceOfExpression =
                        (PsiInstanceOfExpression)expression;
                if (isAgreeing(instanceOfExpression)) {
                    agreeingInstanceof = true;
                } else if (isConflicting(instanceOfExpression)) {
                    conflictingInstanceof = true;
                }
            }
        }

        private boolean isConflicting(PsiInstanceOfExpression expression) {
            final PsiExpression conditionOperand = expression.getOperand();
            if (!EquivalenceChecker.expressionsAreEquivalent(
                    referenceExpression, conditionOperand)) {
                return false;
            }
            final PsiTypeElement typeElement = expression.getCheckType();
            if (typeElement == null) {
                return false;
            }
            final PsiType type = typeElement.getType();
            if (strict) {
                return !castType.equals(type);
            } else {
                return !castType.isAssignableFrom(type);
            }
        }

        private boolean isAgreeing(PsiInstanceOfExpression expression) {
            final PsiExpression conditionOperand = expression.getOperand();
            if (!EquivalenceChecker.expressionsAreEquivalent(
                    referenceExpression, conditionOperand)) {
                return false;
            }
            final PsiTypeElement typeElement = expression.getCheckType();
            if (typeElement == null) {
                return false;
            }
            final PsiType type = typeElement.getType();
            if (strict) {
                return castType.equals(type);
            } else {
                return castType.isAssignableFrom(type);
            }
        }

        public boolean hasAgreeingInstanceof() {
            return agreeingInstanceof;
        }

        public boolean hasConflictingInstanceof() {
            return conflictingInstanceof;
        }
    }
}