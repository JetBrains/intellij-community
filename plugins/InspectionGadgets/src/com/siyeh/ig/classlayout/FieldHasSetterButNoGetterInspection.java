package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import org.jetbrains.annotations.NotNull;

public class FieldHasSetterButNoGetterInspection extends FieldInspection {

    public String getDisplayName() {
        return "Field has setter but no getter";
    }

    public String getGroupDisplayName() {
        return GroupNames.JAVABEANS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Field '#ref' has setter but no getter #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticNonFinalFieldVisitor();
    }

    private static class StaticNonFinalFieldVisitor extends BaseInspectionVisitor {
 
        public void visitField(@NotNull PsiField field) {
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
