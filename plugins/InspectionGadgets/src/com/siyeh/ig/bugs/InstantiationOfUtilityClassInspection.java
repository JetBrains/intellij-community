package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.UtilityClassUtil;

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new InstantiationOfUtilityClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class InstantiationOfUtilityClassVisitor extends BaseInspectionVisitor{
        private InstantiationOfUtilityClassVisitor(BaseInspection inspection,
                                   InspectionManager inspectionManager,
                                   boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(PsiNewExpression expression){
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