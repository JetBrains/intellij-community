package com.siyeh.ig.j2me;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class MethodCallInLoopConditionInspection extends StatementInspection {

    public String getDisplayName() {
        return "Method call in loop condition";
    }

    public String getGroupDisplayName() {
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to method '#ref()' in loop condition #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MethodCallInLoopConditionVisitor(this, inspectionManager, onTheFly);
    }

    private static class MethodCallInLoopConditionVisitor extends StatementInspectionVisitor {
        private MethodCallInLoopConditionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if(condition== null)
            {
                return;
            }
            checkForMethodCalls(condition);
        }

        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if(condition == null){
                return;
            }
            checkForMethodCalls(condition);
        }


        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if(condition == null){
                return;
            }
            checkForMethodCalls(condition);;
        }

        private void checkForMethodCalls(PsiExpression condition){
            final PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor(){
                public void visitMethodCallExpression(PsiMethodCallExpression expression){
                    super.visitMethodCallExpression(expression);
                    registerMethodCallError(expression);
                }
            };
            condition.accept(visitor);
        }

    }

}
