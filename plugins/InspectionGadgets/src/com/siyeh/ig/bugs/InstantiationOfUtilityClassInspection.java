package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

public class InstantiationOfUtilityClassInspection extends ExpressionInspection{

    public String getDisplayName(){
        return "Instantiation of utility class";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Instantiation of utility class '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new InstantiationOfUtilityClassVisitor();
    }

    private static class InstantiationOfUtilityClassVisitor extends BaseInspectionVisitor{

        public void visitNewExpression(@NotNull PsiNewExpression expression){
            final PsiType type = expression.getType();
            if(!(type instanceof PsiClassType))
            {
                return;
            }
            final PsiClass aClass = ((PsiClassType) type).resolve();
            if(aClass == null)
            {
                return;
            }
            if(!UtilityClassUtil.isUtilityClass(aClass)){
                return;
            }
            final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
            registerError(classReference);
        }
    }
}