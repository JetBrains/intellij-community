package com.siyeh.ig.bugs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class MisspelledCompareToInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix("compareTo");

    public String getDisplayName() {
        return "'compareto()' instead of 'compareTo()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() method should probably be compareTo() #loc";
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return false;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MisspelledCompareToVisitor();
    }

    private static class MisspelledCompareToVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super
            final String methodName = method.getName();
            if (!"compareto".equals(methodName)) {
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
