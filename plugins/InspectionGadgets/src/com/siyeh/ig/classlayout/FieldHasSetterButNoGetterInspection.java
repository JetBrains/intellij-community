package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;

public class FieldHasSetterButNoGetterInspection extends FieldInspection {

    public String getDisplayName() {
        return "Field has setter but no getter";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Field '#ref' has setter but no getter #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StaticNonFinalFieldVisitor(this, inspectionManager, onTheFly);
    }

    private static class StaticNonFinalFieldVisitor extends BaseInspectionVisitor {
        private StaticNonFinalFieldVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            final PsiManager psiManager = field.getManager();
            final Project project = psiManager.getProject();
            final String propertyName =
                    PropertyUtil.suggestPropertyName(project, field);
            final boolean isStatic =
                    field.hasModifierProperty(PsiModifier.STATIC);
            final PsiClass containingClass = field.getContainingClass();
            final PsiMethod setter = PropertyUtil.findPropertySetter(containingClass,
                                                                     propertyName, isStatic,
                                                                     false);
            if(setter==null){
                return;
            }
            final PsiMethod getter =
                    PropertyUtil.findPropertyGetter(containingClass,
                                                    propertyName,
                                                    isStatic,
                                                    false);
            if(getter != null){
                return;
            }
            registerFieldError(field);
        }
    }
}
