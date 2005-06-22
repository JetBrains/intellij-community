package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import org.jetbrains.annotations.NotNull;

public class EmptyClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Empty class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref is empty #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyClassVisitor();
    }

    private static class EmptyClassVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            //don't call super, to prevent drilldown
            if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
                return;
            }
            if(aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass){
                return;
            }
            final PsiMethod[] constructors = aClass.getConstructors();
            if (constructors != null && constructors.length > 0) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            if (methods != null && methods.length > 0) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            if (fields != null && fields.length > 0) {
                return;
            }
            registerClassError(aClass);
        }
    }
}