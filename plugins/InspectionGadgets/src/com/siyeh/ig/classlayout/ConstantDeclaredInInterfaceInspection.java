package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import org.jetbrains.annotations.NotNull;

public class ConstantDeclaredInInterfaceInspection extends FieldInspection {

    public String getDisplayName() {
        return "Constant declared in interface";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Constant '#ref' declared in interface #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConstantDeclaredInInterfaceVisitor();
    }

    private static class ConstantDeclaredInInterfaceVisitor extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            //no call to super, so we don't drill into anonymous classes
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!containingClass.isInterface() && !containingClass.isAnnotationType()) {
                return;
            }
            registerFieldError(field);
        }
    }
}
