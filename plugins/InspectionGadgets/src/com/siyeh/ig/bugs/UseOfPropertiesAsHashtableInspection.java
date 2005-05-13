package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class UseOfPropertiesAsHashtableInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Use of Properties object as a Hashtable";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){

        return "Call to Hashtable.#ref() on properties object #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new SystemSetSecurityManagerVisitor(this, inspectionManager,
                                                   onTheFly);
    }

    private static class SystemSetSecurityManagerVisitor
                                                         extends BaseInspectionVisitor{
        private SystemSetSecurityManagerVisitor(BaseInspection inspection,
                                                InspectionManager inspectionManager,
                                                boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);

            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!isHashtableMethod(methodName)){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            if(!ClassUtils.isSubclass(containingClass, "java.util.Hashtable")){
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if(qualifier == null){
                return;
            }
            if(!TypeUtils.expressionHasTypeOrSubtype("java.util.Properties",
                                                     qualifier)){
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isHashtableMethod(String name){
            return "put".equals(name) || "putAll".equals(name) || "get"
                    .equals(name);
        }

    }
}
