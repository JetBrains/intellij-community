package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class ThreeNegationsPerMethodInspection extends MethodInspection {

    public String getDisplayName() {
        return "Method with more than three negations";
    }

    public String getGroupDisplayName() {
        return GroupNames.METHODMETRICS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method = (PsiMethod) location.getParent();
        final NegationCountVisitor visitor = new NegationCountVisitor();
        method.accept(visitor);
        final int negationCount = visitor.getCount();
        return "#ref contains " + negationCount + " negations #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ThreeNegationsPerMethodVisitor(this, inspectionManager, onTheFly);
    }

    private static class ThreeNegationsPerMethodVisitor extends BaseInspectionVisitor {
        private ThreeNegationsPerMethodVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            // note: no call to super
            final NegationCountVisitor visitor = new NegationCountVisitor();
            method.accept(visitor);
            final int negationCount = visitor.getCount();
            if (negationCount <= 3) {
                return;
            }
            registerMethodError(method);
        }
    }

}
