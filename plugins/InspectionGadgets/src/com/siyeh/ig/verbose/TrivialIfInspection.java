package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionEquivalenceChecker;

public class TrivialIfInspection extends ExpressionInspection {
    private final TrivialIfFix fix = new TrivialIfFix();

    public String getDisplayName() {
        return "Unnecessary 'if' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    protected BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TrivialIfVisitor(this, inspectionManager, onTheFly);
    }

    public String buildErrorString(PsiElement location) {
        final PsiIfStatement ifStatement = (PsiIfStatement) location.getParent();
        final PsiExpression condition = ifStatement.getCondition();
        return "'if(" + condition.getText() + ")...'  can be simplified to '" +
                calculateReplacementStatement(ifStatement) +
                "' #loc";
    }

    private static String calculateReplacementStatement(PsiIfStatement statement) {
        PsiStatement thenBranch = statement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        final PsiExpression condition = statement.getCondition();
        final String replacementString;
        if (thenBranch instanceof PsiReturnStatement) {
            if (isReturn(thenBranch, "true")) {
                replacementString = "return " + condition.getText() + ';';
            } else {
                replacementString =
                        "return " + BoolUtils.getNegatedExpressionText(condition) + ';';
            }
        } else {
            final PsiExpressionStatement expressionStatement = (PsiExpressionStatement) thenBranch;
            final PsiAssignmentExpression assignment = (PsiAssignmentExpression) expressionStatement.getExpression();

            final PsiExpression lhs = assignment.getLExpression();
            final PsiJavaToken sign = assignment.getOperationSign();
            if (isAssignment(thenBranch, "true")) {
                replacementString = lhs.getText() + ' ' +
                        sign.getText() + ' ' + condition.getText() + ';';
            } else {
                replacementString = lhs.getText() + ' ' +
                        sign.getText() + ' ' +
                        BoolUtils.getNegatedExpressionText(condition) + ';';
            }
        }
        return replacementString;
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class TrivialIfFix extends InspectionGadgetsFix {
        public String getName() {
            return "Simplify";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiElement ifKeywordElement = descriptor.getPsiElement();
            final PsiIfStatement ifStatement = (PsiIfStatement) ifKeywordElement.getParent();
            final String newStatement = calculateReplacementStatement(ifStatement);
            replaceStatement(project, ifStatement, newStatement);
        }
    }

    private static class TrivialIfVisitor extends BaseInspectionVisitor {
        private TrivialIfVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            if (statement.getCondition() == null) {
                return;
            }
            PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            thenBranch = ControlFlowUtils.stripBraces(thenBranch);
            PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            elseBranch = ControlFlowUtils.stripBraces(elseBranch);

            if (isReturn(thenBranch, "true") && isReturn(elseBranch, "false")) {
                registerStatementError(statement);
                return;
            }
            if (isReturn(thenBranch, "false") && isReturn(elseBranch, "true")) {
                registerStatementError(statement);
                return;
            }
            if (isAssignment(thenBranch, "true") && isAssignment(elseBranch, "false") &&
                    areCompatibleAssignments(thenBranch, elseBranch)) {
                registerStatementError(statement);
                return;
            }
            if (isAssignment(thenBranch, "false") && isAssignment(elseBranch, "true") &&
                    areCompatibleAssignments(thenBranch, elseBranch)) {
                registerStatementError(statement);
                return;
            }
        }

    }

    private static boolean isReturn(PsiStatement statement, String value) {
        if (statement == null) {
            return false;
        }
        if (!(statement instanceof PsiReturnStatement)) {
            return false;
        }
        final PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null) {
            return false;
        }
        final String returnValueString = returnValue.getText();
        return value.equals(returnValueString);
    }

    private static boolean isAssignment(PsiStatement statement, String value) {
        if (statement == null) {
            return false;
        }
        if (!(statement instanceof PsiExpressionStatement)) {
            return false;
        }
        final PsiExpression expression = ((PsiExpressionStatement) statement).getExpression();
        if (!(expression instanceof PsiAssignmentExpression)) {
            return false;
        }
        final PsiExpression rhs = ((PsiAssignmentExpression) expression).getRExpression();
        if (rhs == null) {
            return false;
        }
        final String rhsText = rhs.getText();
        return value.equals(rhsText);
    }

    private static boolean areCompatibleAssignments(PsiStatement statement1, PsiStatement statement2) {
        final PsiExpressionStatement expressionStatement1 = (PsiExpressionStatement) statement1;
        final PsiAssignmentExpression expression1 = (PsiAssignmentExpression) expressionStatement1.getExpression();
        final PsiExpressionStatement expressionStatement2 = (PsiExpressionStatement) statement2;
        final PsiAssignmentExpression expression2 = (PsiAssignmentExpression) expressionStatement2.getExpression();

        final PsiJavaToken sign2 = expression2.getOperationSign();
        if (sign2 == null) {
            return false;
        }
        final String operand2 = sign2.getText();
        final PsiJavaToken sign1 = expression1.getOperationSign();
        if (sign1 == null) {
            return false;
        }
        final String operand1 = sign1.getText();
        if (!operand2.equals(operand1)) {
            return false;
        }
        final PsiExpression lhs1 = expression1.getLExpression();
        final PsiExpression lhs2 = expression2.getLExpression();
        return ExpressionEquivalenceChecker.expressionsAreEquivalent(lhs1, lhs2);
    }

}
