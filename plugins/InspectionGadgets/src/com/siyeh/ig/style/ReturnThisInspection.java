package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class ReturnThisInspection extends ExpressionInspection {
    public String getID(){
        return "ReturnOfThis";
    }
    public String getDisplayName() {
        return "Return of 'this'";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Return of '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ReturnThisVisitor();
    }

    private static class ReturnThisVisitor extends BaseInspectionVisitor {

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
            if (parent == null || !(parent instanceof PsiReturnStatement)) {
                return;
            }
            registerError(thisValue);
        }
    }

}
