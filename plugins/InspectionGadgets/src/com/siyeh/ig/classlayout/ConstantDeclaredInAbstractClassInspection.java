package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class ConstantDeclaredInAbstractClassInspection extends FieldInspection {

    public String getDisplayName() {
        return "Constant declared in abstract class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Constant '#ref' declared in abstract class #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConstantDeclaredInAbstractClassVisitor();
    }

    private static class ConstantDeclaredInAbstractClassVisitor extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            //no call to super, so we don't drill into anonymous classes
            if (!field.hasModifierProperty(PsiModifier.STATIC) ||
                    !field.hasModifierProperty(PsiModifier.PUBLIC) ||
                    !field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.isInterface() || containingClass.isAnnotationType()) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            registerFieldError(field);
        }
    }
}
