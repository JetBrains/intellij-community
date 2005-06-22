package com.siyeh.ig.j2me;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new MethodCallInLoopConditionVisitor();
    }

    private static class MethodCallInLoopConditionVisitor extends StatementInspectionVisitor {
        public void visitForStatement(@NotNull PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if(condition== null)
            {
                return;
            }
            checkForMethodCalls(condition);
        }

        public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if(condition == null){
                return;
            }
            checkForMethodCalls(condition);
        }


        public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if(condition == null){
                return;
            }
            checkForMethodCalls(condition);;
        }

        private void checkForMethodCalls(PsiExpression condition){
            final PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor(){
                public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
                    super.visitMethodCallExpression(expression);
                    registerMethodCallError(expression);
                }
            };
            condition.accept(visitor);
        }

    }

}
