package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class SystemOutErrInspection extends ExpressionInspection {
    public String getID(){
        return "UseOfSystemOutOrSystemErr";
    }
    public String getDisplayName() {
        return "Use of System.out or System.err";
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }



    public String buildErrorString(PsiElement location) {
        return "Uses of System.out and System.err should probably be replaced with more robust logging #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SystemOutErrVisitor(this, inspectionManager, onTheFly);
    }

    private static class SystemOutErrVisitor extends BaseInspectionVisitor {
        private SystemOutErrVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);

            final String name = expression.getReferenceName();
            if (!"out".equals(name) && !"err".equals(name)) {
                return;
            }
            final PsiElement referent = expression.resolve();
            if(!(referent instanceof PsiField))
            {
               return;
            }
            final PsiClass containingClass = ((PsiMember) referent).getContainingClass();
            if(containingClass == null)
            {
                return;
            }
            final String className = containingClass.getQualifiedName();
            if(!"java.lang.System".equals(className))
            {
                return;
            }
            registerError(expression);
        }

    }

}
