package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;

public class ConstantIfStatementInspection
        extends StatementInspection{
    private final ConstantIfStatementFix fix = new ConstantIfStatementFix();

    public String getDisplayName(){
        return "Constant if statement";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ConstantIfStatementVisitor();
    }

    public String buildErrorString(PsiElement location){
        return "'#ref' statement can be simplified #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class
            ConstantIfStatementFix extends InspectionGadgetsFix{
        public String getName(){
            return "Simplify";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement ifKeyword = descriptor.getPsiElement();
            final PsiIfStatement statement =
                    (PsiIfStatement) ifKeyword.getParent();
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiStatement elseBranch = statement.getElseBranch();
            final PsiExpression condition = statement.getCondition();
            if(isFalse(condition)){
                if(elseBranch != null){
                    final String elseText = elseBranch.getText();
                    replaceStatement(statement, elseText);
                } else{
                    deleteElement(statement);
                }
            } else{
                final String thenText = thenBranch.getText();
                replaceStatement(statement, thenText);
            }
        }
    }

    private static class ConstantIfStatementVisitor
            extends BaseInspectionVisitor{

        public void visitIfStatement(PsiIfStatement statment){
            super.visitIfStatement(statment);
            final PsiExpression condition = statment.getCondition();
            if(condition == null){
                return;
            }
            final PsiStatement thenBranch = statment.getThenBranch();
            if(thenBranch == null){
                return;
            }
            if(isTrue(condition) || isFalse(condition)){
                registerStatementError(statment);
            }
        }
    }

    private static boolean isFalse(PsiExpression expression){
        final String text = expression.getText();
        return "false".equals(text);
    }

    private static boolean isTrue(PsiExpression expression){
        final String text = expression.getText();
        return "true".equals(text);
    }
}
