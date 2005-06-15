package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InnerClassVariableHidesOuterClassVariableInspection extends FieldInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreInvisibleFields = true;
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "InnerClassFieldHidesOuterClassField";
    }

    public String getDisplayName() {
        return "Inner class field hides outer class field";
    }

    public String getGroupDisplayName() {
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore outer fields not visible from inner class",
                this, "m_ignoreInvisibleFields");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Inner class field '#ref' hides outer class field #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InnerClassVariableHidesOuterClassVariableVisitor();
    }

    private class InnerClassVariableHidesOuterClassVariableVisitor extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String fieldName = field.getName();
            if ("serialVersionUID".equals(fieldName)) {
                return;    //special case
            }
            boolean reportStaticsOnly = false;
            if(aClass.hasModifierProperty(PsiModifier.STATIC))
            {
                reportStaticsOnly = true;
            }
            PsiClass ancestorClass =
                    ClassUtils.getContainingClass(aClass);
            while (ancestorClass != null) {
                final PsiField ancestorField = ancestorClass.findFieldByName(fieldName, false);
                if (ancestorField != null) {
                    if (!m_ignoreInvisibleFields ||
                            !reportStaticsOnly || field.hasModifierProperty(PsiModifier.STATIC)) {
                       registerFieldError(field);
                    }
                }
                ancestorClass = ClassUtils.getContainingClass(ancestorClass);
            }
        }
    }

}
