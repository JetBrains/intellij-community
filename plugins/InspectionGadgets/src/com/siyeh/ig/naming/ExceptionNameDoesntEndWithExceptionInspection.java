package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class ExceptionNameDoesntEndWithExceptionInspection extends ClassInspection {
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "ExceptionClassNameDoesntEndWithException";
    }
    public String getDisplayName() {
        return "Exception class name doesn't end with Exception";
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Exception class name '#ref' doesn't end with 'Exception' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionNameDoesntEndWithExceptionVisitor();
    }

    private static class ExceptionNameDoesntEndWithExceptionVisitor extends BaseInspectionVisitor {


        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down into inner classes
            final String className = aClass.getName();
            if (className == null) {
                return;
            }
            if (className.endsWith("Exception")) {
                return;
            }
            if (!ClassUtils.isSubclass(aClass, "java.lang.Exception")) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
