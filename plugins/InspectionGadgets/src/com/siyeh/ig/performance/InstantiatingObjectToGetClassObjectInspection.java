package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.siyeh.ig.*;

public class InstantiatingObjectToGetClassObjectInspection extends ExpressionInspection {
    private final InstantiatingObjectToGetClassObjectFix fix = new InstantiatingObjectToGetClassObjectFix();

    public String getDisplayName() {
        return "Instantiating object to get Class object";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Instantiating object to get Class object #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class InstantiatingObjectToGetClassObjectFix
            extends InspectionGadgetsFix{

        public String getName(){
            return "Replace with direct class object access";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression) descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            final PsiType type = qualifier.getType();
            final String text = type.getPresentableText();
            final String newExpression = text + ".class";
            replaceExpression(project, expression, newExpression);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SystemGCVisitor(this, inspectionManager, onTheFly);
    }

    private static class SystemGCVisitor extends BaseInspectionVisitor {
        private SystemGCVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"getClass".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null)
            {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null || args.length!=0)
            {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if(qualifier == null)
            {
                return;
            }
            if(!(qualifier instanceof PsiNewExpression))
            {
                return;
            }
            registerError(expression);
        }
    }

}
