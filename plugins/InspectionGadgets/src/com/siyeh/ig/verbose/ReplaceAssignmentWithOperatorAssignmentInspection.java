package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ExpressionEquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;

public class ReplaceAssignmentWithOperatorAssignmentInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Assignment replaceable with operator assignment";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref could be simplified to " +
                calculateReplacementExpression((PsiAssignmentExpression) location) + " #loc";
    }

    private static String calculateReplacementExpression(PsiAssignmentExpression expression) {
        final PsiBinaryExpression rhs = (PsiBinaryExpression) expression.getRExpression();
        final PsiExpression lhs = expression.getLExpression();
        final PsiJavaToken sign = rhs.getOperationSign();
        final PsiExpression rhsRhs = rhs.getROperand();
        return lhs.getText() + ' ' + sign.getText() + "= " + rhsRhs.getText();
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ReplaceAssignmentWithOperatorAssignmentVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new ReplaceAssignmentWithOperatorAssignmentFix((PsiAssignmentExpression) location);
    }

    private static class ReplaceAssignmentWithOperatorAssignmentFix extends InspectionGadgetsFix {
        private final String m_name;

        private ReplaceAssignmentWithOperatorAssignmentFix(PsiAssignmentExpression expression) {
            super();
            final PsiBinaryExpression rhs = (PsiBinaryExpression) expression.getRExpression();
            final PsiJavaToken sign = rhs.getOperationSign();
            m_name = "Replace = with " + sign.getText() + '=';
        }

        public String getName() {
            return m_name;
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiAssignmentExpression expression =
                    (PsiAssignmentExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression(project, expression, newExpression);
        }

    }

    private static class ReplaceAssignmentWithOperatorAssignmentVisitor extends BaseInspectionVisitor {
        private ReplaceAssignmentWithOperatorAssignmentVisitor(BaseInspection inspection,
                                                               InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
            super.visitAssignmentExpression(assignment);
            final PsiJavaToken sign = assignment.getOperationSign();
            if (sign == null) {
                return;
            }
            if (!sign.getTokenType().equals(JavaTokenType.EQ)) {
                return;
            }
            final PsiExpression lhs = assignment.getLExpression();
            final PsiExpression rhs = assignment.getRExpression();
            if (lhs == null || rhs == null) {
                return;
            }
            if (!(rhs instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryRhs = (PsiBinaryExpression) rhs;
            final PsiJavaToken operatorSign = binaryRhs.getOperationSign();
            if (operatorSign.getTokenType().equals(JavaTokenType.OROR) ||
                    operatorSign.getTokenType().equals(JavaTokenType.ANDAND)) {
                return;
            }
            final PsiExpression lOperand = binaryRhs.getLOperand();
            if (lOperand == null) {
                return;
            }
            final PsiExpression rOperand = binaryRhs.getROperand();
            if (rOperand == null) {
                return;
            }
            if (SideEffectChecker.mayHaveSideEffects(lhs)) {
                return;
            }
            if (!ExpressionEquivalenceChecker.expressionsAreEquivalent(lhs, lOperand)) {
                return;
            }
            registerError(assignment);
        }
    }
}
