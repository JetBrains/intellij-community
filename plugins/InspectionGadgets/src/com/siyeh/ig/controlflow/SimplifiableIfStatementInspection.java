/**
 * (c) 2006 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: 20060310, 6:13:35 PM
 */
package com.siyeh.ig.controlflow;

import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimplifiableIfStatementInspection
        extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "simplifiable.if.statement.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SimplifiableIfStatementVisitor();
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiIfStatement statement = (PsiIfStatement)infos[0];
        return InspectionGadgetsBundle.message(
                "simplifiable.if.statement.problem.descriptor",
                calculateReplacementStatement(statement));
    }

    static String calculateReplacementStatement(
            PsiIfStatement statement) {
        PsiStatement thenBranch = statement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        if (thenBranch == null) {
            return "";
        }
        PsiStatement elseBranch = statement.getElseBranch();
        elseBranch = ControlFlowUtils.stripBraces(elseBranch);
        if (elseBranch == null) {
            final PsiElement nextStatement =
                    PsiTreeUtil.skipSiblingsForward(statement,
                            PsiWhiteSpace.class);
            if (nextStatement instanceof PsiStatement) {
                elseBranch = (PsiStatement)nextStatement;
            }
        }
        if (elseBranch == null) {
            return "";
        }
        final PsiExpression condition = statement.getCondition();
        if (condition == null) {
            return "";
        }
        if (elseBranch instanceof PsiReturnStatement) {
            final PsiReturnStatement thenReturnStatement =
                    (PsiReturnStatement)thenBranch;
            final PsiExpression thenReturnValue =
                    thenReturnStatement.getReturnValue();
            if (thenReturnValue == null) {
                return "";
            }
            final PsiReturnStatement elseReturnStatement =
                    (PsiReturnStatement)elseBranch;
            final PsiExpression elseReturnValue =
                    elseReturnStatement.getReturnValue();
            if (elseReturnValue == null) {
                return "";
            }
            if (BoolUtils.isTrue(thenReturnValue)) {
                return "return " + condition.getText() + " || " +
                        elseReturnValue.getText() + ';';
            } else if (BoolUtils.isFalse(thenReturnValue)) {
                return "return " +
                        BoolUtils.getNegatedExpressionText(condition) + " && " +
                        elseReturnValue.getText() + ';';
            }
            if (BoolUtils.isTrue(elseReturnValue)) {
                return "return " +
                        BoolUtils.getNegatedExpressionText(condition) + " || " +
                        thenReturnValue.getText() + ';';
            } else {
                return "return " + condition.getText() + " && " +
                        thenReturnValue.getText() + ';';
            }
        }
        final PsiExpressionStatement thenStatement =
                (PsiExpressionStatement)thenBranch;
        final PsiExpressionStatement  elseStatement =
                (PsiExpressionStatement)elseBranch;
        final PsiAssignmentExpression thenAssignment =
                (PsiAssignmentExpression)thenStatement.getExpression();
        final PsiAssignmentExpression elseAssignment =
                (PsiAssignmentExpression)elseStatement.getExpression();
        final PsiExpression lExpression = thenAssignment.getLExpression();
        final PsiExpression thenRhs = thenAssignment.getRExpression();
        if (thenRhs == null) {
            return "";
        }
        final PsiExpression elseRhs = elseAssignment.getRExpression();
        if (elseRhs == null) {
            return "";
        }
        final PsiJavaToken token = elseAssignment.getOperationSign();
        if (BoolUtils.isTrue(thenRhs)) {
            return lExpression.getText() + ' ' + token.getText() + ' ' +
                    condition.getText() + " || " + elseRhs.getText() + ';';
        } else if (BoolUtils.isFalse(thenRhs)) {
            return lExpression.getText() + ' ' + token.getText() + ' ' +
                    BoolUtils.getNegatedExpressionText(condition) + " && " +
                    elseRhs.getText() + ';';
        }
        if (BoolUtils.isTrue(elseRhs)) {
            return lExpression.getText() + ' ' + token.getText() + ' ' +
                    BoolUtils.getNegatedExpressionText(condition) + " || " +
                    thenRhs.getText() + ';';
        } else {
            return lExpression.getText() + ' ' + token.getText() + ' ' +
                    condition.getText() + " && " + thenRhs.getText() + ';';
        }
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new SimplifiableIfStatementFix();
    }

    private static class SimplifiableIfStatementFix
            extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "constant.conditional.expression.simplify.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiIfStatement ifStatement =
                    (PsiIfStatement)element.getParent();
            final String newStatement =
                    calculateReplacementStatement(ifStatement);
            if (ifStatement.getElseBranch() == null) {
                final PsiElement nextStatement =
                        PsiTreeUtil.skipSiblingsForward(ifStatement,
                                PsiWhiteSpace.class);
                if (nextStatement != null) {
                    nextStatement.delete();
                }
            }
            replaceStatement(ifStatement, newStatement);
        }
    }

    private static class SimplifiableIfStatementVisitor
            extends BaseInspectionVisitor {

        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            if (statement.getCondition() == null) {
                return;
            }
            if (!(isReplaceableAssignment(statement) ||
                    isReplaceableReturn(statement))) {
                return;
            }
            registerStatementError(statement, statement);
        }

        public static boolean isReplaceableReturn(PsiIfStatement ifStatement) {
            PsiStatement thenBranch = ifStatement.getThenBranch();
            thenBranch = ControlFlowUtils.stripBraces(thenBranch);
            PsiStatement elseBranch = ifStatement.getElseBranch();
            elseBranch = ControlFlowUtils.stripBraces(elseBranch);
            if (elseBranch == null) {
                final PsiElement nextStatement =
                        PsiTreeUtil.skipSiblingsForward(ifStatement,
                                PsiWhiteSpace.class);
                if (nextStatement instanceof PsiStatement) {
                    elseBranch = (PsiStatement)nextStatement;
                }
            }
            if (!(thenBranch instanceof PsiReturnStatement) ||
                    !(elseBranch instanceof PsiReturnStatement)) {
                return false;
            }
            final PsiExpression thenReturn =
                    ((PsiReturnStatement)thenBranch).getReturnValue();
            if (thenReturn == null) {
                return false;
            }
            final PsiExpression elseReturn =
                    ((PsiReturnStatement)elseBranch).getReturnValue();
            if (elseReturn == null) {
                return false;
            }
            final boolean thenConstant = BoolUtils.isFalse(thenReturn) ||
                    BoolUtils.isTrue(thenReturn);
            final boolean elseConstant = BoolUtils.isFalse(elseReturn) ||
                    BoolUtils.isTrue(elseReturn);
            return thenConstant != elseConstant;
        }

        public static boolean isReplaceableAssignment(
                PsiIfStatement ifStatement) {
            PsiStatement thenBranch = ifStatement.getThenBranch();
            if (thenBranch == null) {
                return false;
            }
            thenBranch = ControlFlowUtils.stripBraces(thenBranch);
            if (thenBranch == null || !isAssignment(thenBranch)) {
                return false;
            }
            PsiStatement elseBranch = ifStatement.getElseBranch();
            elseBranch = ControlFlowUtils.stripBraces(elseBranch);
            if (elseBranch == null || !isAssignment(elseBranch)) {
                return false;
            }
            final PsiExpressionStatement thenStatement =
                    (PsiExpressionStatement)thenBranch;
            final PsiAssignmentExpression thenExpression =
                    (PsiAssignmentExpression)thenStatement.getExpression();
            final PsiExpressionStatement elseStatement =
                    (PsiExpressionStatement)elseBranch;
            final PsiAssignmentExpression elseExpression =
                    (PsiAssignmentExpression)elseStatement.getExpression();
            final PsiJavaToken thenOperationSign =
                    thenExpression.getOperationSign();
            final IElementType thenTokenType = thenOperationSign.getTokenType();
            final PsiJavaToken elseOperationSign =
                    elseExpression.getOperationSign();
            final IElementType elseTokenType = elseOperationSign.getTokenType();
            if (!thenTokenType.equals(elseTokenType)) {
                return false;
            }
            final PsiExpression thenRhs = thenExpression.getRExpression();
            if (thenRhs == null) {
                return false;
            }
            final PsiExpression elseRhs = elseExpression.getRExpression();
            if (elseRhs == null) {
                return false;
            }
            final boolean thenConstant = BoolUtils.isFalse(thenRhs) ||
                    BoolUtils.isTrue(thenRhs);
            final boolean elseConstant = BoolUtils.isFalse(elseRhs) ||
                    BoolUtils.isTrue(elseRhs);
            if (thenConstant == elseConstant) {
                return false;
            }
            final PsiExpression thenLhs = thenExpression.getLExpression();
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                    elseLhs);
        }

        public static boolean isAssignment(@Nullable PsiStatement statement) {
            if (!(statement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement)statement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            return expression instanceof PsiAssignmentExpression;
        }
    }
}