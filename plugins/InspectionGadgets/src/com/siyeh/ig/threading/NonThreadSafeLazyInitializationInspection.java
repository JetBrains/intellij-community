package com.siyeh.ig.threading;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class NonThreadSafeLazyInitializationInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Non-thread-safe lazy initialization of static field";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Lazy initialization of static field '#ref' is not thread-safe #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DoubleCheckedLockingVisitor();
    }

    private static class DoubleCheckedLockingVisitor extends BaseInspectionVisitor {

        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
        }

    }
}
