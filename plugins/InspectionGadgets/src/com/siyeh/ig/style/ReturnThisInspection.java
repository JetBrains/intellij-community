package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class ReturnThisInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Return of 'this'";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Return of '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ReturnThisVisitor(this, inspectionManager, onTheFly);
    }

    private static class ReturnThisVisitor extends BaseInspectionVisitor {
        private ReturnThisVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }


        public void visitThisExpression(PsiThisExpression thisValue) {
            super.visitThisExpression(thisValue);
            if (thisValue.getQualifier() != null) {
                return;
            }
            PsiElement parent = thisValue.getParent();
            while (parent != null &&
                    (parent instanceof PsiParenthesizedExpression ||
                    parent instanceof PsiConditionalExpression ||
                    parent instanceof PsiTypeCastExpression)) {
                parent = parent.getParent();
            }
            if (parent == null || !(parent instanceof PsiReturnStatement)) {
                return;
            }
            registerError(thisValue);
        }
    }

}
