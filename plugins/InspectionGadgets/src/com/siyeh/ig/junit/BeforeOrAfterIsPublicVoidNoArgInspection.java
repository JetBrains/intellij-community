package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class BeforeOrAfterIsPublicVoidNoArgInspection extends MethodInspection {

    public String getID() {
        return "BeforeOrAfterWithIncorrectSignature";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message("before.or.after.is.public.void.no.arg.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new BeforeOrAfterIsPublicVoidNoArgVisitor();
    }

    private static class BeforeOrAfterIsPublicVoidNoArgVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super;
            if (!AnnotationUtil.isAnnotated(method, "org.junit.Before", true) &&
                    !AnnotationUtil.isAnnotated(method, "org.junit.After", true)) {
                return;
            }
            final PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return;
            }
            final PsiClass targetClass = method.getContainingClass();
            if (targetClass == null) {
                return;
            }

            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null) {
                return;
            }
            if (parameters.length != 0) {
                registerMethodError(method);
            }
            else if (!returnType.equals(PsiType.VOID)) {
                registerMethodError(method);
            }
            else if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                registerMethodError(method);
            }
            else if (method.hasModifierProperty(PsiModifier.STATIC)) {
                registerMethodError(method);
            }
        }
    }
}
