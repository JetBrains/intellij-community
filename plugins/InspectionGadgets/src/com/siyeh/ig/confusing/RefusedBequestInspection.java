package com.siyeh.ig.confusing;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class RefusedBequestInspection extends MethodInspection{
    public String getDisplayName(){
        return "Refused bequest";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Method #ref ignores defined method in superclass #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new RefusedBequestVisitor();
    }

    private static class RefusedBequestVisitor extends BaseInspectionVisitor{


        public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            final PsiCodeBlock body = method.getBody();
            if(body == null){
                return;
            }
            PsiMethod leastConcreteSuperMethod = null;
            final PsiMethod[] superMethods =
                    PsiSuperMethodUtil.findSuperMethods(method, true);
            for(final PsiMethod superMethod : superMethods){
                final PsiClass containingClass =
                        superMethod.getContainingClass();
                if(!superMethod.hasModifierProperty(PsiModifier.ABSTRACT) &&
                        !containingClass.isInterface()){
                    leastConcreteSuperMethod = superMethod;
                    break;
                }
            }
            if(leastConcreteSuperMethod == null){
                return;
            }
            final PsiClass containingClass =
                    leastConcreteSuperMethod.getContainingClass();
            final String className = containingClass.getQualifiedName();
            if("java.lang.Object".equals(className)){
                return;
            }
            if(containsSuperCall(body, leastConcreteSuperMethod)){
                return;
            }

            registerMethodError(method);
        }
    }

    private static boolean containsSuperCall(PsiCodeBlock body,
                                             PsiMethod method){
        final SuperCallVisitor visitor = new SuperCallVisitor(method);
        body.accept(visitor);
        return visitor.hasSuperCall();
    }

    private static class SuperCallVisitor extends PsiRecursiveElementVisitor{
        private PsiMethod methodToSearchFor;
        private boolean hasSuperCall = false;

        SuperCallVisitor(PsiMethod methodToSearchFor){
            super();
            this.methodToSearchFor = methodToSearchFor;
        }

        public void visitElement(@NotNull PsiElement element){
            if(!hasSuperCall){
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            if(hasSuperCall){
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier == null){
                return;
            }
            final String text = qualifier.getText();
            if(!"super".equals(text)){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            if(method.equals(methodToSearchFor)){
                hasSuperCall = true;
            }
        }

        public boolean hasSuperCall(){
            return hasSuperCall;
        }
    }
}
