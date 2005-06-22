package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class MethodNamesDifferOnlyByCaseInspection extends MethodInspection {
    public String getID(){
        return "MethodNamesDifferingOnlyByCase";
    }
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Method names differing only by case";
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public String buildErrorString(Object arg) {
        return "Method names '#ref' and '" + arg + "' differ only by case";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OverloadedMethodsWithSameNumberOfParametersVisitor();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class OverloadedMethodsWithSameNumberOfParametersVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            if (method.isConstructor()) {
                return;
            }
            final PsiIdentifier nameIdentifier = method.getNameIdentifier();
            if (nameIdentifier == null) {
                return;
            }
            final String methodName = method.getName();
            if (methodName == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(PsiMethod testMethod : methods){
                final String testMethName = testMethod.getName();
                if(testMethName != null && !methodName.equals(testMethName) &&
                        methodName.equalsIgnoreCase(testMethName)){
                    registerError(nameIdentifier, testMethName);
                }
            }
        }
    }

}
