package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class ParameterOfConcreteClassInspection extends MethodInspection {
    public String getID(){
        return "MethodParameterOfConcreteClass";
    }
    public String getDisplayName() {
        return "Method parameter of concrete class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(Object arg) {
        return "Parameter " + arg + " of concrete class #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ParameterOfConcreteClassVisitor();
    }

    private static class ParameterOfConcreteClassVisitor extends BaseInspectionVisitor {

        public void visitParameter(@NotNull PsiParameter parameter) {
            super.visitParameter(parameter);

            if (parameter.getDeclarationScope() instanceof PsiCatchSection) {
                return;
            }
            final PsiTypeElement typeElement = parameter.getTypeElement();
            if (!ConcreteClassUtil.typeIsConcreteClass(typeElement)) {
                return;
            }
            final String variableName = parameter.getName();
            registerError(typeElement, variableName);
        }
    }

}
