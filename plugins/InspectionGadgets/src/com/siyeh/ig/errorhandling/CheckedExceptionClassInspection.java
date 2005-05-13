package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class CheckedExceptionClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Checked exception class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Checked exception class #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CheckedExceptionClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class CheckedExceptionClassVisitor extends BaseInspectionVisitor {
        private CheckedExceptionClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(@NotNull PsiClass aClass) {
            if (!ClassUtils.isSubclass(aClass, "java.lang.Throwable")) {
                return;
            }
            if (ClassUtils.isSubclass(aClass, "java.lang.RuntimeException")) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
