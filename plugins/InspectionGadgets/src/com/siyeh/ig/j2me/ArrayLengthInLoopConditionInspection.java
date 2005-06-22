package com.siyeh.ig.j2me;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ArrayLengthInLoopConditionInspection extends StatementInspection {

    public String getDisplayName() {
        return "Array.length in loop condition";
    }

    public String getGroupDisplayName() {
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Check of array .#ref in loop condition #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ArrayLengthInLoopConditionVisitor();
    }

    private static class ArrayLengthInLoopConditionVisitor extends StatementInspectionVisitor {
        

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
            checkForMethodCalls(condition);
        }

        private void checkForMethodCalls(PsiExpression condition){
            final PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor(){
                public void visitReferenceExpression(@NotNull PsiReferenceExpression expression){
                    super.visitReferenceExpression(expression);
                    final String name = expression.getReferenceName();
                    if(!"length".equals(name))
                    {
                        return;
                    }
                    final PsiExpression qualifier = expression.getQualifierExpression();
                    if(qualifier == null)
                    {
                        return;
                    }
                    final PsiType type = qualifier.getType();
                    if(!(type instanceof PsiArrayType))
                    {
                        return;
                    }
                    final PsiElement lengthElement = expression.getReferenceNameElement();
                    registerError(lengthElement);
                }
            };
            condition.accept(visitor);
        }

    }

}
