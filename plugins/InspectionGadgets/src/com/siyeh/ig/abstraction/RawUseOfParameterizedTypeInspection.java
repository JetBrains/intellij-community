package com.siyeh.ig.abstraction;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.VariableInspection;
import org.jetbrains.annotations.NotNull;

public class RawUseOfParameterizedTypeInspection extends VariableInspection {

    public String getDisplayName() {
        return "Raw use of parameterized class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Raw use of parameterized class #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RawUseOfParameterizedTypeVisitor();
    }

    private static class RawUseOfParameterizedTypeVisitor extends BaseInspectionVisitor {


        public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final PsiTypeElement typeElement = variable.getTypeElement();
            checkTypeElement(typeElement);
        }

        public void visitTypeCastExpression(@NotNull PsiTypeCastExpression cast) {
            super.visitTypeCastExpression(cast);
            final PsiTypeElement typeElement = cast.getCastType();
            checkTypeElement(typeElement);
        }

        public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression){
            super.visitInstanceOfExpression(expression);
            final PsiTypeElement typeElement = expression.getCheckType();
            checkTypeElement(typeElement);
        }

        public void visitNewExpression(@NotNull PsiNewExpression newExpression){
            super.visitNewExpression(newExpression);
            final PsiJavaCodeReferenceElement classReference =
                    newExpression.getClassReference();

            if(classReference == null){
                return;
            }
            if(newExpression.getTypeArgumentList() != null){
                return;
            }
            final PsiElement referent = classReference.resolve();
            if(!(referent instanceof PsiClass)){
                return;
            }

            final PsiClass referredClass = (PsiClass) referent;
            if(!referredClass.hasTypeParameters()){
                return;
            }
            registerError(classReference);
        }

        private void checkTypeElement(PsiTypeElement typeElement){
            if(typeElement == null){
                return;
            }
            final PsiType type = typeElement.getType();
            if(!(type instanceof PsiClassType)){
                return;
            }

            final PsiClassType classType = (PsiClassType) type;
            if(classType.hasParameters()){
                return;
            }
            final PsiClass aClass = classType.resolve();

            if(aClass == null){
                return;
            }
            if(!aClass.hasTypeParameters()){
                return;
            }
            final PsiElement typeNameElement =
                    typeElement.getInnermostComponentReferenceElement();
            registerError(typeNameElement);
        }
    }

}
