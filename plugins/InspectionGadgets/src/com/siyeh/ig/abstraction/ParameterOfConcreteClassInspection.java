package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class ParameterOfConcreteClassInspection extends MethodInspection {

    public String getDisplayName() {
        return "Concrete class for method parameter";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(Object arg) {
        return "Parameter " + arg + " of concrete class #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new LocalVariableOfConcreteClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class LocalVariableOfConcreteClassVisitor extends BaseInspectionVisitor {
        private LocalVariableOfConcreteClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitParameter(PsiParameter parameter) {
            super.visitParameter(parameter);

            if (parameter.getDeclarationScope() instanceof PsiTryStatement) {
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
