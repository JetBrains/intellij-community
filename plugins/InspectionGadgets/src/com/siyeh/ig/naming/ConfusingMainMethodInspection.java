package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.TypeUtils;

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ConfusingMainMethodVisitor(this, inspectionManager, onTheFly);
    }

    private static class ConfusingMainMethodVisitor extends BaseInspectionVisitor {
        private ConfusingMainMethodVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod aMethod) {
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
