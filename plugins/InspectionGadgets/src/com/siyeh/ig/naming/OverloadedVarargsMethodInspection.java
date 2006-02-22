/**
 * (c) 2004 Carp Technologies BV
 * Hengelosestraat 705, 7521PA Enschede
 * Created: Feb 22, 2006, 12:39:10 AM
 */
package com.siyeh.ig.naming;

import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class OverloadedVarargsMethodInspection extends MethodInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "overloaded.vararg.method.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod element = (PsiMethod)location.getParent();
        if (element.isConstructor()) {
            return InspectionGadgetsBundle.message(
                    "overloaded.vararg.constructor.problem.descriptor");
        } else {
            return InspectionGadgetsBundle.message(
                    "overloaded.vararg.method.problem.descriptor");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OverloadedVarargMethodVisitor();
    }

    private static class OverloadedVarargMethodVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            if (!hasVarargParameter(method)) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String methodName = method.getName();
            final PsiMethod[] sameNameMethods;
            if (method.isConstructor()) {
                sameNameMethods = aClass.findMethodsByName(methodName, false);
            } else {
                sameNameMethods = aClass.findMethodsByName(methodName, false);
            }
            for (PsiMethod sameNameMethod : sameNameMethods) {
                if(!sameNameMethod.equals(method)) {
                    registerMethodError(method);
                }
            }
        }

        private static boolean hasVarargParameter(PsiMethod method) {
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length == 0) {
                return false;
            }
            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            return lastParameter.isVarArgs();
        }
    }
}