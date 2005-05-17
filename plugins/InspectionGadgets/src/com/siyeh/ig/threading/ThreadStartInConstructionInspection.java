package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class ThreadStartInConstructionInspection extends ExpressionInspection{
    public String getID(){
        return "CallToThreadStartDuringObjectConstruction";
    }

    public String getDisplayName(){
        return "Call to 'Thread.start()' during object construction";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to Thread.#ref() during object construction #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ThreadStartInConstructionVisitor();
    }

    private static class ThreadStartInConstructionVisitor
            extends BaseInspectionVisitor{
        private boolean inConstruction = false;

        public void visitMethod(@NotNull PsiMethod method){
            boolean wasInConstructor = false;
            if(method.isConstructor()){
                inConstruction = true;
                wasInConstructor = inConstruction;
            }
            super.visitMethod(method);
            if(method.isConstructor()){
                inConstruction = wasInConstructor;
            }
        }

        public void visitClassInitializer(@NotNull PsiClassInitializer initializer){
            boolean wasInConstructor = false;
            if(!initializer.hasModifierProperty(PsiModifier.STATIC)){
                inConstruction = true;
                wasInConstructor = inConstruction;
            }
            super.visitClassInitializer(initializer);
            if(!initializer.hasModifierProperty(PsiModifier.STATIC)){
                inConstruction = wasInConstructor;
            }
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if(!inConstruction){
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"start".equals(methodName)){
                return;
            }

            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if(paramList == null){
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if(parameters.length != 0){
                return;
            }
            final PsiClass methodClass = method.getContainingClass();
            if(methodClass == null ||
                       !ClassUtils.isSubclass(methodClass, "java.lang.Thread")){
                return;
            }
            registerMethodCallError(expression);
        }
    }
}
