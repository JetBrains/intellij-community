package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ConfusingMainMethodInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Confusing 'main()' method";
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
        return "Method named '#ref' without signature 'public static void main(String[])' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConfusingMainMethodVisitor();
    }

    private static class ConfusingMainMethodVisitor extends BaseInspectionVisitor {


        public void visitMethod(@NotNull PsiMethod aMethod) {
            // no call to super, so it doesn't drill down into inner classes
            final String methodName = aMethod.getName();
            if (!"main".equals(methodName)) {
                return;
            }
            if (!aMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
                registerMethodError(aMethod);
                return;
            }
            if (!aMethod.hasModifierProperty(PsiModifier.STATIC)) {
                registerMethodError(aMethod);
                return;
            }
            final PsiType returnType = aMethod.getReturnType();

            if (!TypeUtils.typeEquals("void", returnType)) {
                registerMethodError(aMethod);
                return;
            }

            final PsiParameterList paramList = aMethod.getParameterList();
            if (paramList == null) {
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                registerMethodError(aMethod);
                return;
            }
            final PsiType paramType = parameters[0].getType();
            if (!TypeUtils.typeEquals("java.lang.String[]", paramType)) {
                registerMethodError(aMethod);
            }
        }

    }

}
