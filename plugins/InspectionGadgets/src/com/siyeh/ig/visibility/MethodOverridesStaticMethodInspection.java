package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;

import java.util.Set;
import java.util.HashSet;

public class MethodOverridesStaticMethodInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Method overrides static method of superclass";
    }

    public String getGroupDisplayName() {
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Method '#ref' overrides a static method of a superclass #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MethodOverridesStaticMethodVisitor(this, inspectionManager,
                onTheFly);
    }

    private static class MethodOverridesStaticMethodVisitor
            extends BaseInspectionVisitor {
        private MethodOverridesStaticMethodVisitor(BaseInspection inspection,
                                                   InspectionManager inspectionManager,
                                                   boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String methName = method.getName();
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null) {
                return;
            }
            final int numParameters = parameters.length;
            PsiClass ancestorClass = aClass.getSuperClass();
            final Set visitedClasses = new HashSet();
            while (ancestorClass != null) {
                if (!visitedClasses.add(ancestorClass)) {
                    return;
                }
                final PsiMethod[] methods = ancestorClass.findMethodsByName(methName, false);
                if (methods != null) {
                    for (int i = 0; i < methods.length; i++) {
                        final PsiMethod testMethod = methods[i];
                        final PsiParameterList testParametersList = testMethod.getParameterList();
                        if (testParametersList == null) {
                            continue;
                        }
                        final int numTestParameters = testParametersList.getParameters().length;
                        if (numParameters != numTestParameters) {
                            continue;
                        }
                        if (testMethod.hasModifierProperty(PsiModifier.STATIC) &&
                                !testMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
                            registerMethodError(method);
                            return;
                        }
                    }
                }
                ancestorClass = ancestorClass.getSuperClass();
            }
        }

    }

}