package com.siyeh.ig.verbose;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.VariableUsedInInnerClassVisitor;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryFinalOnParameterInspection extends MethodInspection {
    public String getID(){
        return "UnnecessaryFinalForMethodParameter";
    }

    public String getDisplayName() {
        return "Unnecessary 'final' for method parameter";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiModifierList modifierList = (PsiModifierList) location.getParent();
        assert modifierList != null;
        final PsiParameter parameter = (PsiParameter) modifierList.getParent();
        assert parameter != null;
        final String parameterName = parameter.getName();
        return "Unnecessary #ref for parameter " + parameterName + " #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryFinalOnParameterVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RemoveModifierFix(location);
    }

    private static class UnnecessaryFinalOnParameterVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null) {
                return;
            }
            for(final PsiParameter parameter : parameters){
                checkParameter(method, parameter);
            }
        }

        private void checkParameter(PsiMethod method, PsiParameter parameter) {
            if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();

            if (containingClass != null) {
                if (containingClass.isInterface() || containingClass.isAnnotationType()) {
                    registerModifierError(PsiModifier.FINAL, parameter);
                    return;
                }
            }

            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                registerModifierError(PsiModifier.FINAL, parameter);
                return;
            }
            final VariableUsedInInnerClassVisitor visitor
                    = new VariableUsedInInnerClassVisitor(parameter);
            method.accept(visitor);
            if (!visitor.isUsedInInnerClass()) {
                registerModifierError(PsiModifier.FINAL, parameter);
            }
        }

    }

}
