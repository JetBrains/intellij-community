package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class InstanceofThisInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "'instanceof' check for 'this'";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'instanceof' check for #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new InstanceofThisVisitor(this, inspectionManager, onTheFly);
    }

    private static class InstanceofThisVisitor extends BaseInspectionVisitor {
        private InstanceofThisVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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
            if (parent == null || !(parent instanceof PsiInstanceOfExpression)) {
                return;
            }
            registerError(thisValue);
        }
    }

}
