package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.MethodCallUtils;

public class ThreadDumpStackInspection extends ExpressionInspection {
    public String getID(){
        return "CallToThreadDumpStack";
    }
    public String getDisplayName() {
        return "Call to 'Thread.dumpStack()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to Thread.#ref() should probably be replaced with more robust logging #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ThreadDumpStackVisitor(this, inspectionManager, onTheFly);
    }

    private static class ThreadDumpStackVisitor extends BaseInspectionVisitor {
        private ThreadDumpStackVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final String methodName = MethodCallUtils.getMethodName(expression);
            if (!"dumpStack".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            if (argumentList.getExpressions().length != 0) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final PsiElement element = methodExpression.resolve();
            if (!(element instanceof PsiMethod)) {
                return;
            }
            final PsiMethod method = (PsiMethod) element;
            final PsiClass aClass = method.getContainingClass();
            final String qualifiedName = aClass.getQualifiedName();
            if (!"java.lang.Thread".equals(qualifiedName)) {
                return;
            }
            registerMethodCallError(expression);
        }

    }

}
