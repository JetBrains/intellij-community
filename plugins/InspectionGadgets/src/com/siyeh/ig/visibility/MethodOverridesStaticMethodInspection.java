package com.siyeh.ig.visibility;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class MethodOverridesStaticMethodInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "MethodOverridesStaticMethodOfSuperclass";
    }
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

    public BaseInspectionVisitor buildVisitor() {
        return new MethodOverridesStaticMethodVisitor();
    }

    private static class MethodOverridesStaticMethodVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
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
            final Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
            while (ancestorClass != null) {
                if (!visitedClasses.add(ancestorClass)) {
                    return;
                }
                final PsiMethod[] methods = ancestorClass.findMethodsByName(methName, false);
                if (methods != null) {
                    for(final PsiMethod testMethod : methods){
                        final PsiParameterList testParametersList = testMethod.getParameterList();
                        if(testParametersList == null){
                            continue;
                        }
                        final int numTestParameters = testParametersList.getParameters().length;
                        if(numParameters != numTestParameters){
                            continue;
                        }
                        if(testMethod.hasModifierProperty(PsiModifier.STATIC) &&
                                !testMethod.hasModifierProperty(PsiModifier.PRIVATE)){
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