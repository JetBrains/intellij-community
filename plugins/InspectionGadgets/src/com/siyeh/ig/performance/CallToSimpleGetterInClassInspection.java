package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class CallToSimpleGetterInClassInspection extends ExpressionInspection{
    public String getID(){
        return "CallToSimpleGetterFromWithinClass";
    }

    public String getDisplayName(){
        return "Call to simple getter from within class";
    }
    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to simple getter '#ref()' from within class #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new CallToSimpleGetterInClassVisitor(this, inspectionManager,
                                                    onTheFly);
    }


    private class CallToSimpleGetterInClassVisitor
            extends BaseInspectionVisitor{
        private CallToSimpleGetterInClassVisitor(BaseInspection inspection,
                                                 InspectionManager inspectionManager,
                                                 boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);
            final PsiExpressionList argList = call.getArgumentList();
            if(argList == null){
                return;
            }
            final PsiExpression[] args = argList.getExpressions();
            if(args == null || args.length != 0){
                return;
            }
            final PsiClass containingClass =
                    (PsiClass) PsiTreeUtil.getParentOfType(call,
                                                           PsiClass.class);
            if(containingClass == null){
                return;
            }
            final PsiMethod method = call.resolveMethod();
            if(method == null){
                return;
            }
            if(!containingClass.equals(method.getContainingClass())){
                return;
            }
            if(!isSimpleGetter(method)){
                return;
            }
            registerMethodCallError(call);
        }
    }

    private boolean isSimpleGetter(PsiMethod method){
        final PsiCodeBlock body = method.getBody();
        if(body == null){
            return false;
        }
        final PsiStatement[] statements = body.getStatements();
        if(statements == null || statements.length != 1){
            return false;
        }
        final PsiStatement statement = statements[0];
        if(!(statement instanceof PsiReturnStatement)){
            return false;
        }
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) statement;
        final PsiExpression value = returnStatement.getReturnValue();
        if(value == null){
            return false;
        }
        if(!(value instanceof PsiReferenceExpression))
        {
           return false;
        }

        final PsiReferenceExpression reference = (PsiReferenceExpression) value;
        final PsiExpression qualifier = reference.getQualifierExpression();
        if(qualifier!=null && !"this".equals(qualifier.getText()))
        {
            return false;
        }
        final PsiElement referent = reference.resolve();
        if(referent == null)
        {
            return false;
        }
        if(!(referent instanceof PsiField))
        {
            return false;
        }
        final PsiField field = (PsiField) referent;
        return field.getContainingClass().equals(method.getContainingClass());
    }
}
