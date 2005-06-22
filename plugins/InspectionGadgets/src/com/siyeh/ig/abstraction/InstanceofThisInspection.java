package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new InstanceofThisVisitor();
    }

    private static class InstanceofThisVisitor extends BaseInspectionVisitor {

        public void visitThisExpression(@NotNull PsiThisExpression thisValue) {
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
