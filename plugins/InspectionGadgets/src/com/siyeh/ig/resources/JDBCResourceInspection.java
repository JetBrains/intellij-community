package com.siyeh.ig.resources;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class JDBCResourceInspection extends ExpressionInspection {
    private static String[] creationMethodClassName =
            new String[]{
                "java.sql.Driver",
                "java.sql.DriverManager",
                "java.sql.Connection",
                "java.sql.Connection",
                "java.sql.Connection",
                "java.sql.Statement",
                "java.sql.Statement",
            };
    private static String[] creationMethodName =
            new String[]{
                "connect",
                "getConnection",
                "createStatement",
                "prepareStatement",
                "prepareCall",
                "executeQuery",
                "getResultSet",
            };

    public String getID(){
        return "JDBCResourceOpenedButNotSafelyClosed";
    }
    public String getDisplayName() {
        return "JDBC resource opened but not safely closed";
    }

    public String getGroupDisplayName() {
        return GroupNames.RESOURCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiExpression expression = (PsiExpression) location;
        final PsiType type = expression.getType();
        final String text = type.getPresentableText();
        return "JDBC " + text + " should be opened in a try block, and closed in a finally block #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new JDBCResourceVisitor(this, inspectionManager, onTheFly);
    }

    private static class JDBCResourceVisitor extends BaseInspectionVisitor {
        private JDBCResourceVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isJDBCResourceCreation(expression)) {
                return;
            }
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiAssignmentExpression)) {
                registerError(expression);
                return;
            }
            final PsiAssignmentExpression assignment = (PsiAssignmentExpression) parent;
            final PsiExpression lhs = assignment.getLExpression();
            if (!(lhs instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiElement referent = ((PsiReferenceExpression) lhs).resolve();
            if (referent == null || !(referent instanceof PsiVariable)) {
                return;
            }
            final PsiVariable boundVariable = (PsiVariable) referent;

            PsiElement currentContext = expression;
            while (true) {
                final PsiTryStatement tryStatement =
                        (PsiTryStatement) PsiTreeUtil.getParentOfType(currentContext, PsiTryStatement.class);
                if (tryStatement == null) {
                    registerError(expression);
                    return;
                }
                if (resourceIsOpenedInTryAndClosedInFinally(tryStatement, expression, boundVariable)) {
                    return;
                }
                currentContext = tryStatement;
            }
        }


        private static boolean resourceIsOpenedInTryAndClosedInFinally(PsiTryStatement tryStatement,
                                                                       PsiExpression lhs, PsiVariable boundVariable) {
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock == null) {
                return false;
            }
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return false;
            }
            if (!PsiTreeUtil.isAncestor(tryBlock, lhs, true)) {
                return false;
            }
            return containsResourceClose(finallyBlock, boundVariable);
        }

        private static boolean containsResourceClose(PsiCodeBlock finallyBlock, PsiVariable boundVariable) {
            final ResourceCloseVisitor visitor = new ResourceCloseVisitor(boundVariable);
            finallyBlock.accept(visitor);
            return visitor.containsResourceClose();
        }

    }

    private static class ResourceCloseVisitor extends PsiRecursiveElementVisitor {
        private boolean containsResourceClose = false;
        private PsiVariable streamToClose;

        private ResourceCloseVisitor(PsiVariable streamToClose) {
            super();
            this.streamToClose = streamToClose;
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"close".equals(methodName)) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiElement referent = ((PsiReferenceExpression) qualifier).resolve();
            if(referent ==null){
                return;
            }
            if (referent.equals(streamToClose)) {
                containsResourceClose = true;
            }
        }

        public boolean containsResourceClose() {
            return containsResourceClose;
        }
    }

    private static boolean isJDBCResourceCreation(PsiMethodCallExpression expression) {
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
            return false;
        }
        final PsiClass containingClass = method.getContainingClass();
        final String name = method.getName();
        if (containingClass == null) {
            return false;
        }
        final String className = containingClass.getQualifiedName();
        for (int i = 0; i < creationMethodName.length; i++) {
            if (name.equals(creationMethodName[i]) &&
                    className.equals(creationMethodClassName[i])) {
                return true;
            }
        }
        return false;
    }

}
