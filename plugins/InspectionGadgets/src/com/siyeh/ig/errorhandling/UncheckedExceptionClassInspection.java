package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

public class UncheckedExceptionClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Unchecked exception class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unchecked exception class #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UncheckedExceptionClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class UncheckedExceptionClassVisitor extends BaseInspectionVisitor {
        private UncheckedExceptionClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            if (!ClassUtils.isSubclass(aClass, "java.lang.Throwable")) {
                return;
            }
            if (ClassUtils.isSubclass(aClass, "java.lang.Exception") &&
                    !ClassUtils.isSubclass(aClass, "java.lang.RuntimeException")) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
