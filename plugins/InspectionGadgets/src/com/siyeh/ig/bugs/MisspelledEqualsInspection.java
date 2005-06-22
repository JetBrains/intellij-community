package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class MisspelledEqualsInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix("equals");

    public String getDisplayName() {
        return "'equal()' instead of 'equals()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() method should probably be equals() #loc";
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return false;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MisspelledEqualsVisitor();
    }

    private static class MisspelledEqualsVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super
            final String methodName = method.getName();
            if (!"equal".equals(methodName)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParameters().length != 1) {
                return;
            }
            registerMethodError(method);
        }

    }

}
