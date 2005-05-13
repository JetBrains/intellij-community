package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class SetReplaceableByEnumSetInspection extends ExpressionInspection{

    public String getDisplayName(){
        return "Set replaceable by EnumSet";
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref replaceable by EnumSet #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new SetReplaceableByEnumSetVisitor(this, inspectionManager, onTheFly);
    }
    private static class SetReplaceableByEnumSetVisitor
            extends BaseInspectionVisitor{
        private SetReplaceableByEnumSetVisitor(BaseInspection inspection,
                                          InspectionManager inspectionManager,
                                          boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(@NotNull PsiNewExpression expression){
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if(!(type instanceof PsiClassType)){
               return;
            }
            final PsiClassType classType = (PsiClassType) type;
            if(!classType.hasParameters())
            {
                return;
            }
            final PsiType[] typeArguments =
                    expression.getClassReference().getTypeParameters();
            if(typeArguments == null || typeArguments.length!=1)
            {
                return;
            }
            final PsiType argumentType = typeArguments[0];
            if(!(argumentType instanceof PsiClassType)){
                return;
            }
            if(!TypeUtils.expressionHasTypeOrSubtype("java.util.Set",
                                                     expression)){
                return;
            }
            if(TypeUtils.expressionHasTypeOrSubtype("java.util.EnumSet",
                                                    expression)){
                return;
            }
            final PsiClassType argumentClassType = (PsiClassType) argumentType;
            final PsiClass argumentClass = argumentClassType.resolve();
            if(argumentClass == null)
            {
                return;
            }
            if(!argumentClass.isEnum())
            {
                return;
            }
            final PsiJavaCodeReferenceElement classRef =
                    expression.getClassReference();
            registerError(classRef);
        }
    }
}
