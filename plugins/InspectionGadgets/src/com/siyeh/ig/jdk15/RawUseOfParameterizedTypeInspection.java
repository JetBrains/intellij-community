package com.siyeh.ig.jdk15;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import org.jetbrains.annotations.NotNull;

public class RawUseOfParameterizedTypeInspection extends VariableInspection {

    public String getDisplayName() {
        return "Raw use of parameterized class";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
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
              final PsiManager manager = variable.getManager();
            final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
            if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            checkTypeElement(typeElement);
        }

        public void visitTypeCastExpression(@NotNull PsiTypeCastExpression cast) {
            super.visitTypeCastExpression(cast);
              final PsiManager manager = cast.getManager();
            final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
            if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)) {
                return;
            }
            final PsiTypeElement typeElement = cast.getCastType();
            checkTypeElement(typeElement);
        }

        public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression){
            final PsiManager manager = expression.getManager();
            final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
            if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)) {
                return;
            }
            super.visitInstanceOfExpression(expression);
            final PsiTypeElement typeElement = expression.getCheckType();
            checkTypeElement(typeElement);
        }

        public void visitNewExpression(@NotNull PsiNewExpression newExpression){
              final PsiManager manager = newExpression.getManager();
            final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
            if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)) {
                return;
            }
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
