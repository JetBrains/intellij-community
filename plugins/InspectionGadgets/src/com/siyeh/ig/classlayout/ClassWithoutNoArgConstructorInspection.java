package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class ClassWithoutNoArgConstructorInspection extends ClassInspection {

    public String getDisplayName() {
        return "Class without no-arg constructor";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref has no no-arg constructor #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassWithoutNoArgConstructorVisitor(this, inspectionManager, onTheFly);
    }

    private static class ClassWithoutNoArgConstructorVisitor extends BaseInspectionVisitor {
        private ClassWithoutNoArgConstructorVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
                return;
            }
            if (aClass.getNameIdentifier() == null) {
                return; //a very hacky test for anonymous classes
            }
            if (classHasNoArgConstructor(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

        private static boolean classHasNoArgConstructor(PsiClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                final PsiMethod method = methods[i];
                if (method.isConstructor()) {
                    final PsiParameterList parameterList = method.getParameterList();
                    if (parameterList != null) {
                        final PsiParameter[] parameters = parameterList.getParameters();
                        if (parameters != null && parameters.length == 0) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

}
