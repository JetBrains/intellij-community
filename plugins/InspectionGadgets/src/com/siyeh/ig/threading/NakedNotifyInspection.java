package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class NakedNotifyInspection extends MethodInspection {

    public String getDisplayName() {
        return "'notify()' or 'notifyAll()' without corresponding state change";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to #ref() without corresponding state change #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NakedNotifyVisitor();
    }

    private static class NakedNotifyVisitor extends BaseInspectionVisitor {
      
        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (!method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body != null) {
                checkBody(body);
            }
        }

        public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiCodeBlock body = statement.getBody();
            if (body != null) {
                checkBody(body);
            }
        }

        private void checkBody(PsiCodeBlock body) {
            final PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                return;
            }
            final PsiStatement firstStatement = statements[0];
            if (!(firstStatement instanceof PsiExpressionStatement)) {
                return;
            }
            final PsiExpression firstExpression =
                    ((PsiExpressionStatement) firstStatement).getExpression();
            if (!(firstExpression instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) firstExpression;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();

            if (!"notify".equals(methodName) && !"notifyAll".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            if (argumentList.getExpressions().length != 0) {
                return;
            }
            registerMethodCallError(methodCallExpression);
        }
    }

}
