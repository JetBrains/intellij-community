package com.siyeh.ig.visibility;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class FieldHidesSuperclassFieldInspection extends FieldInspection {
    /** @noinspection PublicField*/
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

    public BaseInspectionVisitor buildVisitor() {
        return new FieldHidesSuperclassFieldVisitor();
    }

    private class FieldHidesSuperclassFieldVisitor extends BaseInspectionVisitor {


        public void visitField(@NotNull PsiField field) {
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String fieldName = field.getName();
            if ("serialVersionUID".equals(fieldName)) {
                return;    //special case
            }
            PsiClass ancestorClass = aClass.getSuperClass();
            final Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
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
