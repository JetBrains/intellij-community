package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.WellFormednessUtils;

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

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiAssignmentExpression expression =
                    (PsiAssignmentExpression) descriptor.getPsiElement();
            final PsiExpression leftExpression = expression.getLExpression();
            final PsiExpression rightExpression = expression.getRExpression();
            final String newExpression = leftExpression.getText() + "==" + rightExpression.getText();
            replaceExpression(project, expression, newExpression);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AssignmentUsedAsConditionVisitor(this, inspectionManager, onTheFly);
    }

    private static class AssignmentUsedAsConditionVisitor extends BaseInspectionVisitor {
        private AssignmentUsedAsConditionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
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
