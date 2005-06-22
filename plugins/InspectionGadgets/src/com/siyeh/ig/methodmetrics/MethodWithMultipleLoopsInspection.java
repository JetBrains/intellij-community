package com.siyeh.ig.methodmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class MethodWithMultipleLoopsInspection extends MethodInspection {

    public String getDisplayName() {
        return "Method with multiple loops";
    }

    public String getGroupDisplayName() {
        return GroupNames.METHODMETRICS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method = (PsiMethod) location.getParent();
        final LoopCountVisitor visitor = new LoopCountVisitor();
        assert method != null;
        method.accept(visitor);
        final int negationCount = visitor.getCount();
        return "#ref contains " + negationCount + " loops #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodWithMultipleLoopsVisitor();
    }

    private static class MethodWithMultipleLoopsVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            final LoopCountVisitor visitor = new LoopCountVisitor();
            method.accept(visitor);
            final int negationCount = visitor.getCount();
            if (negationCount <= 1) {
                return;
            }
            registerMethodError(method);
        }
    }

}
