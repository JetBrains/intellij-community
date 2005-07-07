package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.MakeProtectedFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NonProtectedConstructorInAbstractClassInspection extends MethodInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreNonPublicClasses = false;
    private final MakeProtectedFix fix = new MakeProtectedFix();

    public String getID(){
        return "ConstructorNotProtectedInAbstractClass";
    }
    public String getDisplayName() {
        return "Constructor not 'protected' in 'abstract' class";
    }

    public String getGroupDisplayName() {
        return GroupNames.INHERITANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Constructor '#ref' is not declared 'protected' in 'abstract' class #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore for non-public classes",
                this, "m_ignoreNonPublicClasses");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonProtectedConstructorInAbstractClassVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private class NonProtectedConstructorInAbstractClassVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.isConstructor()) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.PROTECTED)
                    || method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (m_ignoreNonPublicClasses && !containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (containingClass.isEnum()) {
                return;
            }
            registerMethodError(method);
        }

    }


}
