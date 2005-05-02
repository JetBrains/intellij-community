package com.siyeh.ig.security;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SystemSetSecurityManagerVisitor(this, inspectionManager, onTheFly);
    }

    private static class SystemSetSecurityManagerVisitor extends BaseInspectionVisitor {
        private SystemSetSecurityManagerVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(PsiNewExpression expression){
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
