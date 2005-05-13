package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.SingletonUtil;
import org.jetbrains.annotations.NotNull;

public class SingletonInspection extends ClassInspection {

    public String getDisplayName() {
        return "Singleton";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref is a singleton #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SingletonVisitor(this, inspectionManager, onTheFly);
    }

    private static class SingletonVisitor extends BaseInspectionVisitor {
        private SingletonVisitor(BaseInspection inspection,
                                 InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!SingletonUtil.isSingleton(aClass)) {
                return;
            }
            registerClassError(aClass);
        }
    }
}
