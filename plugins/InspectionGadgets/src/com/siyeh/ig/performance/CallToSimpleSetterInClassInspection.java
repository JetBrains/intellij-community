package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

public class CallToSimpleSetterInClassInspection extends ExpressionInspection{
    public String getID(){
        return "CallToSimpleSetterFromWithinClass";
    }

    public String getDisplayName(){
        return "Call to simple setter from within class";
    }
    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to simple setter '#ref()' from within class #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new CallToSimpleSetterInClassVisitor(this, inspectionManager,
                                                    onTheFly);
    }

    private class CallToSimpleSetterInClassVisitor
            extends BaseInspectionVisitor{
        private CallToSimpleSetterInClassVisitor(BaseInspection inspection,
                                                 InspectionManager inspectionManager,
                                                 boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!methodName.startsWith("set")){
                return;
            }
            final PsiExpressionList argList = call.getArgumentList();
            if(argList == null){
                return;
            }
            final PsiExpression[] args = argList.getExpressions();
            if(args == null || args.length != 1){
                return;
            }
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(call);
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
            if(!isSimpleSetter(method)){
                return;
            }
            registerMethodCallError(call);
        }
    }

    private boolean isSimpleSetter(PsiMethod method){
        final PsiCodeBlock body = method.getBody();
        if(body == null){
            return false;
        }
        final PsiStatement[] statements = body.getStatements();
        if(statements == null || statements.length != 1){
            return false;
        }
        final PsiStatement statement = statements[0];
        if(!(statement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpressionStatement possibleAssignmentStatement =
                (PsiExpressionStatement) statement;
        final PsiExpression possibleAssignment =
                possibleAssignmentStatement.getExpression();
        if(possibleAssignment == null){
            return false;
        }
        if(!(possibleAssignment instanceof PsiAssignmentExpression))
        {
           return false;
        }
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression) possibleAssignment;
        final PsiJavaToken sign = assignment.getOperationSign();
        if(!sign.getTokenType().equals(JavaTokenType.EQ)){
            return false;
        }
        final PsiExpression lhs = assignment.getLExpression();
        if(!(lhs instanceof PsiReferenceExpression))
        {
            return false;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
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
        if(!field.getContainingClass().equals(method.getContainingClass()))
        {
            return false;
        }

        final PsiExpression rhs = assignment.getRExpression();
        if(!(rhs instanceof PsiReferenceExpression)){
            return false;
        }
        final PsiReferenceExpression rReference = (PsiReferenceExpression) rhs;
        final PsiExpression rQualifier = reference.getQualifierExpression();
        if(rQualifier != null){
            return false;
        }
        final PsiElement rReferent = rReference.resolve();
        if(rReferent == null){
            return false;
        }
        return rReferent instanceof PsiParameter;

    }
}
