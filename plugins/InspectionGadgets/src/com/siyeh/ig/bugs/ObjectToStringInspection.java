package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class ObjectToStringInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Call to default .toString()";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to default toString() #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectToStringVisitor();
    }

    private static class ObjectToStringVisitor
            extends BaseInspectionVisitor {
        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiType type = expression.getType();
            if(type == null){
                return;
            }
            if(!TypeUtils.isJavaLangString(type)){
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            checkExpression(lhs);
            final PsiExpression rhs = expression.getROperand();
            checkExpression(rhs);
        }

        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();

            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSEQ)) {
                return;
            }
            final PsiExpression lhs = expression.getLExpression();

            final PsiType type = lhs.getType();
            if (type == null) {
                return;
            }
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final PsiExpression rhs = expression.getRExpression();
            checkExpression(rhs);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression
                    .getMethodExpression();
            if(methodExpression == null)
            {
                return;
            }
            final String name = methodExpression.getReferenceName();
            if(!"toString".equals(name))
            {
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            if(argList == null)
            {
                return;
            }
            final PsiExpression[] args = argList.getExpressions();
            if(args.length !=0)
            {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            checkExpression(qualifier);
        }

        private void checkExpression(PsiExpression expression){
            if(expression == null)
            {
                return;
            }
            final PsiType type = expression.getType();
            if(type == null)
            {
                return;
            }
            if(type instanceof PsiArrayType)
            {
                registerError(expression);
                return;
            }
            if(!(type instanceof PsiClassType)){
                return;
            }
            final PsiClassType classType = (PsiClassType) type;
            final PsiClass referencedClass = classType.resolve();
            if(referencedClass == null)
            {
                return;
            }
            if(referencedClass.isEnum() || referencedClass.isInterface())
            {
                return;
            }
            if(!hasGoodToString(referencedClass))
            {
                registerError(expression);
            }
        }

        private boolean hasGoodToString(PsiClass aClass){
            if(aClass == null)
            {
                return false;
            }
            final String className = aClass.getQualifiedName();
            if("java.lang.Object".equals(className))
            {
                return false;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(PsiMethod method : methods){
                if(isToString(method))
                {
                    return true;
                }
            }
            final PsiClass superClass = aClass.getSuperClass();
            return hasGoodToString(superClass);
        }

        private boolean isToString(PsiMethod method){
            final String methodName = method.getName();
            if(!"toString".equals(methodName))
            {
                return false;
            }
            final PsiParameterList paramList = method.getParameterList();
            if(paramList == null)
            {
                return false;
            }
            final PsiParameter[] params = paramList.getParameters();
            return params != null && params.length == 0;
        }
    }


}