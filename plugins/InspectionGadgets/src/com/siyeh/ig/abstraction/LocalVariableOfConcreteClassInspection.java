package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class LocalVariableOfConcreteClassInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Local variable of concrete class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(Object arg) {
        return "Local variable " + arg + " of concrete class #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new LocalVariableOfConcreteClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class LocalVariableOfConcreteClassVisitor extends BaseInspectionVisitor {
        private LocalVariableOfConcreteClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            final PsiTypeElement typeElement = variable.getTypeElement();
            if (!ConcreteClassUtil.typeIsConcreteClass(typeElement)) {
                return;
            }
            final String variableName = variable.getName();
            registerError(typeElement, variableName);
        }
    }

}
