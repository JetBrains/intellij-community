package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class FieldHidesSuperclassFieldInspection extends FieldInspection {
    public boolean m_ignoreInvisibleFields = false;
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "FieldNameHidesFieldInSuperclass";
    }

    public String getDisplayName() {
        return "Field name hides field in superclass";
    }

    public String getGroupDisplayName() {
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Field '#ref' hides field in superclass #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore superclass fields not visible from subclass",
                this, "m_ignoreInvisibleFields");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new FieldHidesSuperclassFieldVisitor(this, inspectionManager, onTheFly);
    }

    private class FieldHidesSuperclassFieldVisitor extends BaseInspectionVisitor {
        private FieldHidesSuperclassFieldVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String fieldName = field.getName();
            if ("serialVersionUID".equals(fieldName)) {
                return;    //special case
            }
            PsiClass ancestorClass = aClass.getSuperClass();
            final Set visitedClasses = new HashSet();
            while (ancestorClass != null) {
                if (!visitedClasses.add(ancestorClass)) {
                    return;
                }
                final PsiField ancestorField = ancestorClass.findFieldByName(fieldName, false);
                if (ancestorField != null) {
                    if (!m_ignoreInvisibleFields || ClassUtils.isFieldVisible(ancestorField, aClass)) {
                        registerFieldError(field);
                        return;
                    }
                }
                ancestorClass = ancestorClass.getSuperClass();
            }
        }

    }

}
