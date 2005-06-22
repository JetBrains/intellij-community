package com.siyeh.ig.visibility;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class MethodOverridesPrivateMethodInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "MethodOverridesPrivateMethodOfSuperclass";
    }
    public String getDisplayName() {
        return "Method overrides private method of superclass";
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
        return "Method '#ref' overrides a private method of a superclass #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodOverridesPrivateMethodVisitor();
    }

    private static class MethodOverridesPrivateMethodVisitor
            extends BaseInspectionVisitor {


        public void visitMethod(@NotNull PsiMethod method) {
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String methodName = method.getName();
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
                final PsiMethod overridingMethod = ancestorClass.findMethodBySignature(method, false);
                if (overridingMethod == null) {
                    //don't trigger if there's a method in that class
                    final PsiMethod[] methods = ancestorClass.findMethodsByName(methodName, false);
                    if (methods != null) {
                        for(final PsiMethod testMethod : methods){
                            final PsiParameterList testParametersList = testMethod.getParameterList();
                            if(testParametersList == null){
                                continue;
                            }
                            final int numTestParameters =
                                    testParametersList.getParameters().length;
                            if(numParameters != numTestParameters){
                                continue;
                            }
                            if(testMethod.hasModifierProperty(PsiModifier.PRIVATE)){
                                registerMethodError(method);
                                return;
                            }
                        }
                    }
                }
                ancestorClass = ancestorClass.getSuperClass();
            }
        }

    }

}