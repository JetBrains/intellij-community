package com.siyeh.ig.controlflow;

import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

public class DuplicateBooleanBranchInspection extends ExpressionInspection {

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "duplicate.boolean.branch.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DuplicateBooleanBranchVisitor();
    }

    private static class DuplicateBooleanBranchVisitor
            extends BaseInspectionVisitor {

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final IElementType tokenType = expression.getOperationSign().getTokenType();
            if (!tokenType.equals(JavaTokenType.ANDAND) && !tokenType.equals(JavaTokenType.OROR)) {
                return;
            }

            PsiElement parent = expression.getParent();
            while (parent instanceof PsiParenthesizedExpression) {
                parent = parent.getParent();
            }
            if (parent instanceof PsiBinaryExpression) {
                final PsiBinaryExpression parentExpression = (PsiBinaryExpression) parent;
                if (tokenType.equals(parentExpression.getOperationSign().getTokenType())) {
                    return;
                }
            }
            final Set<PsiExpression> conditions = new HashSet<PsiExpression>();
            collectConditions(expression, conditions, tokenType);
            final int numConditions = conditions.size();
            if (numConditions < 2) {
                return;
            }
            final PsiExpression[] conditionArray =
                    conditions.toArray(new PsiExpression[numConditions]);
            final boolean[] matched = new boolean[conditionArray.length];
            Arrays.fill(matched, false);
            for (int i = 0; i < conditionArray.length; i++) {
                if (matched[i]) {
                    continue;
                }
                final PsiExpression condition = conditionArray[i];
                for (int j = i + 1; j < conditionArray.length; j++) {
                    if (matched[j]) {
                        continue;
                    }
                    final PsiExpression testCondition = conditionArray[j];
                    final boolean areEquivalent =
                            EquivalenceChecker.expressionsAreEquivalent(
                                    condition, testCondition);
                    if (areEquivalent) {
                        registerError(testCondition);
                        if (!matched[i]) {
                            registerError(condition);
                        }
                        matched[i] = true;
                        matched[j] = true;
                    }
                }
            }
        }

        private static void collectConditions(PsiExpression condition, Set<PsiExpression> conditions,
                                              IElementType tokenType) {
            if (condition == null) {
                return;
            }
            if (condition instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression =
                        (PsiParenthesizedExpression) condition;
                final PsiExpression contents =
                        parenthesizedExpression.getExpression();
                collectConditions(contents, conditions, tokenType);
                return;
            }
            if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) condition;
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final IElementType testTokeType = sign.getTokenType();
                if (testTokeType.equals(tokenType)) {
                    final PsiExpression lhs = binaryExpression.getLOperand();
                    collectConditions(lhs, conditions, tokenType);
                    final PsiExpression rhs = binaryExpression.getROperand();
                    collectConditions(rhs, conditions, tokenType);
                    return;
                }
            }
            conditions.add(condition);
        }
    }
}
