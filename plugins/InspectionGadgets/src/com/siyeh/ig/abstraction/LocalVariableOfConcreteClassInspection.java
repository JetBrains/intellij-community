package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new LocalVariableOfConcreteClassVisitor();
    }

    private static class LocalVariableOfConcreteClassVisitor extends BaseInspectionVisitor {

        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
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
