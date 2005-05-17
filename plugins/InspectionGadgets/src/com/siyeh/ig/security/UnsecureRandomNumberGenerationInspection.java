package com.siyeh.ig.security;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class UnsecureRandomNumberGenerationInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Unsecure random number generation";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final String text = location.getText();
        if("random".equals(text))
        {
            return "For security purposes, use java.security.SecureRandom instead of java.lang.Math.#ref() #loc";
        }
        else if("Random".equals(text))
        {
            return "For security purposes, use java.security.SecureRandom instead of java.util.#ref #loc";
        }
        else
        {
            return "For security purposes, use java.security.SecureRandom instead of #ref #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InsecureRandomNumberGenerationVisitor();
    }

    private static class InsecureRandomNumberGenerationVisitor extends BaseInspectionVisitor {
      
        public void visitNewExpression(@NotNull PsiNewExpression expression){
            super.visitNewExpression(expression);
            if(!TypeUtils.expressionHasTypeOrSubtype("java.util.Random", expression )){
                return;
            }
            final PsiJavaCodeReferenceElement reference = expression.getClassReference();
            if(reference == null)
            {
                return;
            }
            registerError(reference);
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression== null)
            {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"random".equals(methodName))
            {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null)
            {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null)
            {
                return;
            }
            final String className = containingClass.getQualifiedName();
            if(!"java.lang.Math".equals(className))
            {
                return;
            }
            registerMethodCallError(expression);
        }
    }

}
