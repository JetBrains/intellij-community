package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class ExceptionFromCatchWhichDoesntWrapInspection extends StatementInspection {
    public String getID(){
        return "ThrowInsideCatchBlockWhichIgnoresCaughtException";
    }
    public String getDisplayName() {
        return "'throw' inside 'catch' block which ignores the caught exception";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' inside 'catch' block ignores the caught exception #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionFromCatchWhichDoesntWrapVisitor();
    }

    private static class ExceptionFromCatchWhichDoesntWrapVisitor extends StatementInspectionVisitor {

        public void visitThrowStatement(PsiThrowStatement statement){
            super.visitThrowStatement(statement);
            if(!ControlFlowUtils.isInCatchBlock(statement)){
                return;
            }
            final PsiExpression exception = statement.getException();
            if(!(exception instanceof PsiNewExpression)){
                return;
            }
            final PsiNewExpression newExpression = (PsiNewExpression) exception;
            final PsiMethod constructor = newExpression.resolveConstructor();
            if(constructor == null){
                return;
            }
            final PsiExpressionList argumentList =
                    newExpression.getArgumentList();
            if(argumentList == null){
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null){
                return;
            }
            if(argIsCatchParameter(args)){
                return;
            }
            registerStatementError(statement);
        }

        private static boolean argIsCatchParameter(PsiExpression[] args) {
            for(final PsiExpression arg : args){
                if(arg instanceof PsiReferenceExpression){
                    final PsiReferenceExpression ref = (PsiReferenceExpression) arg;
                    final PsiElement referent = ref.resolve();
                    if(referent != null
                            && referent instanceof PsiParameter
                            &&
                            ((PsiParameter) referent).getDeclarationScope() instanceof PsiCatchSection){
                        return true;
                    }
                }
            }
            return false;
        }
    }

}
