package com.siyeh.ipp.commutative;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;

class FlipCommutativeMethodCallPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) element;

        if(expression.getArgumentList() == null){
            return false;
        }

        // do it only when there is just one argument.
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        if(args.length != 1){
            return false;
        }

        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        // make sure that there is a caller and a caller
        if(qualifier == null){
            return false;
        }

        final String methodName = methodExpression.getReferenceName();
        // the logic is...
        // if the argument takes a method of the same name with the caller as parameter
        // then we can switch the argument and the caller.

        final PsiType callerType = qualifier.getType();
        final PsiType argumentType = args[0].getType();

        if(argumentType == null || !(argumentType instanceof PsiClassType)){
            return false;
        }

        if(callerType == null || !(callerType instanceof PsiClassType)){
            return false;
        }

        final PsiClass argumentClass = ((PsiClassType) argumentType).resolve();
        if(argumentClass == null){
            return false;
        }
        final PsiMethod[] methods =
                argumentClass.findMethodsByName(methodName, true);
        for(int i = 0; i < methods.length; i++){
            final PsiMethod testMethod = methods[i];

            final String testMethodName = testMethod.getName();
            if(testMethodName.equals(methodName)){
                final PsiParameterList parameterList =
                        testMethod.getParameterList();
                final PsiParameter[] parameters = parameterList.getParameters();
                if(parameters.length == 1){
                    final PsiParameter parameter = parameters[0];
                    final PsiType type = parameter.getType();
                    if(!(type == null || !type.isAssignableFrom(callerType))){
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
