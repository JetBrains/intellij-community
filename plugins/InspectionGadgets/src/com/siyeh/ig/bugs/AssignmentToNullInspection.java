package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class AssignmentToNullInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Assignment to 'null'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Assignment of variable #ref to null #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentToNullVisitor();
    }

    private static class AssignmentToNullVisitor extends BaseInspectionVisitor {
        public void visitLiteralExpression(@NotNull PsiLiteralExpression value) {
            super.visitLiteralExpression(value);
            final String text = value.getText();
            if (!"null".equals(text)) {
                return;
            }
            PsiElement parent = value.getParent();
            while (parent instanceof PsiParenthesizedExpression ||
                    parent instanceof PsiConditionalExpression ||
                    parent instanceof PsiTypeCastExpression) {
                parent = parent.getParent();
            }
            if (!(parent instanceof PsiAssignmentExpression)) {
                return;
            }
            final PsiExpression lhs = ((PsiAssignmentExpression) parent).getLExpression();
            registerError(lhs);
        }

    }

}
