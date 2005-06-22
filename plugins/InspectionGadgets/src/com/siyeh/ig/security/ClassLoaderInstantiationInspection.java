package com.siyeh.ig.security;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNewExpression;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ClassLoaderInstantiationInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "ClassLoader instantiation";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {

        return "Instantiation of #ref may pose security concerns #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassLoaderInstantiationVisitor();
    }

    private static class ClassLoaderInstantiationVisitor extends BaseInspectionVisitor {

        public void visitNewExpression(@NotNull PsiNewExpression expression){
            super.visitNewExpression(expression);
            if(!TypeUtils.expressionHasTypeOrSubtype("java.lang.ClassLoader", expression )){
                return;
            }
            final PsiJavaCodeReferenceElement reference = expression.getClassReference();
            if(reference == null)
            {
                return;
            }
            registerError(reference);
        }

    }

}
