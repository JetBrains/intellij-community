package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class NonCommentSourceStatementsInspection extends MethodMetricInspection {
    private static final int DEFAULT_LIMIT = 30;

    public String getID(){
        return "OverlyLongMethod";
    }
    public String getDisplayName() {
        return "Overly long method ";
    }

    public String getGroupDisplayName() {
        return GroupNames.METHODMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return DEFAULT_LIMIT;
    }

    protected String getConfigurationLabel() {
        return "Non-comment source statements limit:";
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method = (PsiMethod) location.getParent();
        final NCSSVisitor visitor = new NCSSVisitor();
        method.accept(visitor);
        final int statementCount = visitor.getStatementCount();
        return "#ref is too long (# Non-comment source statements = " + statementCount + ") #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonCommentSourceStatementsMethodVisitor();
    }

    private class NonCommentSourceStatementsMethodVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            final NCSSVisitor visitor = new NCSSVisitor();
            method.accept(visitor);
            final int count = visitor.getStatementCount();

            if (count <= getLimit()) {
                return;
            }
            registerMethodError(method);
        }
    }

}
