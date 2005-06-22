package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class AssignmentUsedAsConditionInspection extends ExpressionInspection {
    private final AssignmentUsedAsConditionFix fix = new AssignmentUsedAsConditionFix();

    public String getDisplayName() {
        return "Assignment used as condition";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref used as condition #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class AssignmentUsedAsConditionFix extends InspectionGadgetsFix {
        public String getName() {
            return "replace '=' with '=='";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiAssignmentExpression expression =
                    (PsiAssignmentExpression) descriptor.getPsiElement();
            final PsiExpression leftExpression = expression.getLExpression();
            final PsiExpression rightExpression = expression.getRExpression();
            assert rightExpression != null;
            final String newExpression = leftExpression.getText() + "==" + rightExpression.getText();
            replaceExpression(expression, newExpression);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentUsedAsConditionVisitor();
    }

    private static class AssignmentUsedAsConditionVisitor extends BaseInspectionVisitor {

        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final PsiElement parent = expression.getParent();
            if(parent == null)
            {
                return;
            }
            if (parent instanceof PsiIfStatement) {
                checkIfStatementCondition((PsiIfStatement) parent, expression);
            }
            if (parent instanceof PsiWhileStatement) {
                checkWhileStatementCondition((PsiWhileStatement) parent, expression);
            }
            if (parent instanceof PsiForStatement) {
                checkForStatementCondition((PsiForStatement) parent, expression);
            }
            if (parent instanceof PsiDoWhileStatement) {
                checkDoWhileStatementCondition((PsiDoWhileStatement) parent, expression);
            }
        }

        private void checkIfStatementCondition(PsiIfStatement ifStatement, PsiAssignmentExpression expression) {
            final PsiExpression condition = ifStatement.getCondition();
            if (expression.equals(condition)) {
                registerError(expression);
            }
        }

        private void checkDoWhileStatementCondition(PsiDoWhileStatement doWhileStatement, PsiAssignmentExpression expression) {
            final PsiExpression condition = doWhileStatement.getCondition();
            if(expression.equals(condition)){
                registerError(expression);
            }
        }

        private void checkForStatementCondition(PsiForStatement forStatement, PsiAssignmentExpression expression) {
            final PsiExpression condition = forStatement.getCondition();
            if(expression.equals(condition)){
                registerError(expression);
            }
        }

        private void checkWhileStatementCondition(PsiWhileStatement whileStatement, PsiAssignmentExpression expression) {
            final PsiExpression condition = whileStatement.getCondition();
            if(expression.equals(condition)){
                registerError(expression);
            }
        }
    }

}
