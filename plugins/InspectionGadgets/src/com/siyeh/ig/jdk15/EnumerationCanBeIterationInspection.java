package com.siyeh.ig.jdk15;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EnumerationCanBeIterationInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return "Enumeration can be iteration";
    }

    @Nls @NotNull
    public String getGroupDisplayName() {
        return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return "Enumeration can be iteration";
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new EnumerationCanBeIterationFix();
    }

    private static class EnumerationCanBeIterationFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return "Replace Enumeration with Iterator";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EnumerationCanBeIterationVisitor();
    }


    static void foo(Vector v, Hashtable h) {
        Enumeration e = v.elements();
        while (e.hasMoreElements()) {
            System.out.println(e.nextElement());
        }
        e = h.elements();
        while (e.hasMoreElements()) {
            System.out.println(e.nextElement());
        }
        e = h.keys();
        Iterator i = h.values().iterator();
        while (i.hasNext()) {
            System.out.println(i.next());
        }
    }

    private static class EnumerationCanBeIterationVisitor
            extends BaseInspectionVisitor {

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName = methodExpression.getReferenceName();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if ("elements".equals(methodName)) {
                if (!TypeUtils.expressionHasTypeOrSubtype(qualifierExpression,
                        "java.util.Vector")) {
                    return;
                }
            } else if ("keys".equals(methodName)) {
                if (!TypeUtils.expressionHasTypeOrSubtype(qualifierExpression,
                        "java.util.Hashtable")) {
                    return;
                }
            } else if ("values".equals(methodName)) {
                if (!TypeUtils.expressionHasTypeOrSubtype(qualifierExpression,
                        "java.util.Hashtable")) {
                    return;
                }
            } else {
                return;
            }
            if (!TypeUtils.expressionHasTypeOrSubtype(expression,
                    "java.util.Enumeration")) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}