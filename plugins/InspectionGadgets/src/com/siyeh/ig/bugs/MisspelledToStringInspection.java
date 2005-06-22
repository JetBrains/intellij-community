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

public class MisspelledToStringInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix("toString");

    public String getDisplayName() {
        return "'tostring()' instead of 'toString()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return false;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() method should probably be toString() #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MisspelledToStringVisitor();
    }

    private static class MisspelledToStringVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super
            final String methodName = method.getName();
            if (!"tostring".equals(methodName)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParameters().length != 0) {
                return;
            }
            registerMethodError(method);
        }

    }

}
