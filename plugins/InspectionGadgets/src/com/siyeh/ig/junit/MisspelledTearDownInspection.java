package com.siyeh.ig.junit;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class MisspelledTearDownInspection extends MethodInspection {

    public String getDisplayName() {
        return "'teardown()' instead of 'tearDown()'";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new RenameFix("tearDown");
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() method should probably be tearDown() #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MisspelledSetUpVisitor();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    private static class MisspelledSetUpVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            final String methodName = method.getName();
            if(!"teardown".equals(methodName)){
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            if (!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
                return;
            }

            registerMethodError(method);
        }

    }

}
