package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class InstantiatingObjectToGetClassObjectInspection extends ExpressionInspection {
    private final InstantiatingObjectToGetClassObjectFix fix = new InstantiatingObjectToGetClassObjectFix();

    public String getDisplayName() {
        return "Instantiating object to get Class object";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
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

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression) descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            final PsiType type = qualifier.getType();
            final String text = type.getPresentableText();
            final String newExpression = text + ".class";
            replaceExpression(expression, newExpression);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemGCVisitor();
    }

    private static class SystemGCVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"getClass".equals(methodName)){
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null){
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null || args.length != 0){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(!(qualifier instanceof PsiNewExpression)){
                return;
            }
            registerError(expression);
        }
    }

}
