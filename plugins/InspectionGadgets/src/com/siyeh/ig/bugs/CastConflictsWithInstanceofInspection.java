/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class CastConflictsWithInstanceofInspection extends ExpressionInspection{

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "cast.conflicts.with.instanceof.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "cast.conflicts.with.instanceof.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new CastConflictsWithInstanceofVisitor();
    }

    private static class CastConflictsWithInstanceofVisitor
            extends BaseInspectionVisitor{

        public void visitTypeCastExpression(
                @NotNull PsiTypeCastExpression expression){
            super.visitTypeCastExpression(expression);
            final PsiTypeElement castTypeElement = expression.getCastType();
            if(castTypeElement == null){
                return;
            }
            final PsiType castType = castTypeElement.getType();
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            if(!(operand instanceof PsiReferenceExpression)){
                return;
            }
            if (!(hasConflictingInstanceOfInConditionalAndBinaryExpressions(
                    expression, castType, operand) ||
                    hasConflictingInstanceOfInIfStatement(
                            expression, operand, castType))) {
                return;
            }
            registerError(castTypeElement);
        }

        private static boolean hasConflictingInstanceOfInIfStatement(
                PsiTypeCastExpression expression, PsiExpression operand,
                PsiType castType) {
            boolean hasConflictingInstanceof = false;
            boolean hasConfirmingInstanceof = false;
            PsiIfStatement currentStatement =
                    PsiTreeUtil.getParentOfType(expression,
                            PsiIfStatement.class);
            while(currentStatement != null){
                if(!isInElse(expression, currentStatement)){
                    final PsiExpression condition =
                            currentStatement.getCondition();
                    if(condition instanceof PsiInstanceOfExpression){
                        final PsiInstanceOfExpression instanceOfCondition =
                                (PsiInstanceOfExpression) condition;
                        if(isConflicting(instanceOfCondition, operand,
                                         castType)){
                            hasConflictingInstanceof = true;
                        } else if(isConfirming(instanceOfCondition, operand,
                                castType)){
                            hasConfirmingInstanceof = true;
                        }
                    }
                }
                currentStatement =
                        PsiTreeUtil.getParentOfType(currentStatement,
                                PsiIfStatement.class);
            }
            return hasConflictingInstanceof && !hasConfirmingInstanceof;
        }

        private static boolean isInElse(PsiExpression expression,
                                        PsiIfStatement statement){
            final PsiStatement branch = statement.getElseBranch();
            return branch != null && PsiTreeUtil.isAncestor(branch, expression,
                    true);
        }

        private static boolean hasConflictingInstanceOfInConditionalAndBinaryExpressions(
                @NotNull PsiTypeCastExpression expression,
                @NotNull PsiType castType,
                @NotNull PsiExpression operand) {
            boolean hasConflictingInstanceof = false;
            boolean hasConfirmingInstanceof = false;
            PsiExpression surroundingExpression = expression;
            while(true){
                surroundingExpression =
                        PsiTreeUtil.getParentOfType(surroundingExpression,
                                PsiConditionalExpression.class,
                                PsiBinaryExpression.class);
                if (surroundingExpression == null) {
                    break;
                }
                final PsiExpression condition;
                if (surroundingExpression instanceof PsiConditionalExpression) {
                    final PsiConditionalExpression conditionalExpression =
                            (PsiConditionalExpression)surroundingExpression;
                    if (isInElse(expression, conditionalExpression)) {
                        continue;
                    }
                    condition = conditionalExpression.getCondition();
                } else {
                    final PsiBinaryExpression binaryExpression =
                            (PsiBinaryExpression)surroundingExpression;
                    final PsiJavaToken sign =
                            binaryExpression.getOperationSign();
                    final IElementType tokenType = sign.getTokenType();
                    PsiExpression lhs =
                            binaryExpression.getLOperand();
                    lhs = ParenthesesUtils.stripParentheses(lhs);
                    if (tokenType == JavaTokenType.ANDAND) {
                        condition = lhs;
                    } else if (tokenType == JavaTokenType.OROR) {
                        if (!(lhs instanceof PsiPrefixExpression)) {
                            continue;
                        }
                        final PsiPrefixExpression prefixExpression =
                                (PsiPrefixExpression)lhs;
                        final PsiJavaToken prefixSign =
                                prefixExpression.getOperationSign();
                        if (prefixSign.getTokenType() != JavaTokenType.EXCL) {
                            continue;
                        }
                        condition = ParenthesesUtils.stripParentheses(
                                prefixExpression.getOperand());
                    } else {
                        continue;
                    }
                }
                if (!(condition instanceof PsiInstanceOfExpression)) {
                    continue;
                }
                final PsiInstanceOfExpression instanceOfCondition =
                        (PsiInstanceOfExpression)condition;
                if (isConflicting(instanceOfCondition, operand, castType)){
                    hasConflictingInstanceof = true;
                } else if(isConfirming(instanceOfCondition, operand, castType)){
                    hasConfirmingInstanceof = true;
                }
            }
            return hasConflictingInstanceof && !hasConfirmingInstanceof;
        }


        private static boolean isInElse(PsiExpression expression,
                                        PsiConditionalExpression conditional){
            final PsiExpression elseExpression =
                    conditional.getElseExpression();
            return elseExpression != null && PsiTreeUtil.isAncestor(
                    elseExpression, expression, true);
        }

        private static boolean isConflicting(PsiInstanceOfExpression condition,
                                             PsiExpression operand,
                                             PsiType castType){
            final PsiExpression conditionOperand = condition.getOperand();
            if(!EquivalenceChecker.expressionsAreEquivalent(operand,
                    conditionOperand)){
                return false;
            }
            final PsiTypeElement typeElement = condition.getCheckType();
            if(typeElement == null){
                return false;
            }
            final PsiType type = typeElement.getType();
            return !castType.isAssignableFrom(type);
        }

        private static boolean isConfirming(PsiInstanceOfExpression condition,
                                            PsiExpression operand,
                                            PsiType castType){
            final PsiExpression conditionOperand = condition.getOperand();
            if(!EquivalenceChecker.expressionsAreEquivalent(operand,
                    conditionOperand)){
                return false;
            }
            final PsiTypeElement typeElement = condition.getCheckType();
            if(typeElement == null){
                return false;
            }
            final PsiType type = typeElement.getType();
            return castType.isAssignableFrom(type);
        }
    }
}