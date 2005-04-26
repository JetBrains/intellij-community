package com.siyeh.ig.security;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

public class CustomClassloaderInspection extends ClassInspection {

    public String getDisplayName() {
        return "Custom ClassLoader";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Custom ClassLoader class '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CustomClassloaderVisitor(this, inspectionManager, onTheFly);
    }

    private static class CustomClassloaderVisitor extends BaseInspectionVisitor {
        private CustomClassloaderVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass){
            if(!ClassUtils.isSubclass(aClass, "java.lang.ClassLoader")) {
                return;
            }
            if("java.lang.ClassLoader".equals(aClass.getQualifiedName())) {
                return;
            }
            registerClassError(aClass);
        }
    }

}
