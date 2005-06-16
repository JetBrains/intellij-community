package com.siyeh.ig.confusing;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionListStatement;
import com.intellij.psi.PsiExpressionStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class NestedAssignmentInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Nested assignment";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested assignment #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NestedAssignmentVisitor();
    }

    private static class NestedAssignmentVisitor extends BaseInspectionVisitor {

        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            final PsiElement parent = expression.getParent();
            if(parent == null)
            {
                return;
            }
            final PsiElement grandparent = parent.getParent();
            if (parent instanceof PsiExpressionStatement ||
                            grandparent instanceof PsiExpressionListStatement) {
                return;
            }
            registerError(expression);
        }
    }

}
