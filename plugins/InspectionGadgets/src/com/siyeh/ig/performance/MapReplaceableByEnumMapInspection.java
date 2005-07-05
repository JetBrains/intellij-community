package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class MapReplaceableByEnumMapInspection extends ExpressionInspection{

    public String getDisplayName(){
        return "Map replaceable by EnumMap";
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref replaceable by EnumMap #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new SetReplaceableByEnumSetVisitor();
    }
    private static class SetReplaceableByEnumSetVisitor
            extends BaseInspectionVisitor{

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
            final PsiType[] typeArguments = classType.getParameters();
            if(typeArguments.length != 2){
                return;
            }
            final PsiType argumentType = typeArguments[0];
            if(!(argumentType instanceof PsiClassType)){
                return;
            }
            if(!TypeUtils.expressionHasTypeOrSubtype("java.util.Map",
                                                     expression)){
                return;
            }
            if(TypeUtils.expressionHasTypeOrSubtype("java.util.EnumMap",
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
