package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import org.jetbrains.annotations.NotNull;

public class StaticVariableOfConcreteClassInspection extends FieldInspection {

    public String getDisplayName() {
        return "Static variable of concrete class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(Object arg) {
        return "Static variable " + arg + " of concrete class #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticVariableOfConcreteClassVisitor();
    }

    private static class StaticVariableOfConcreteClassVisitor extends BaseInspectionVisitor {


        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiTypeElement typeElement = field.getTypeElement();
            if (!ConcreteClassUtil.typeIsConcreteClass(typeElement)) {
                return;
            }
            final String variableName = field.getName();
            registerError(typeElement, variableName);
        }
    }

}
