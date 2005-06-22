package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class SafeLockInspection extends ExpressionInspection{
    public String getID(){
        return "LockAcquiredButNotSafelyReleased";
    }

    public String getDisplayName(){
        return "Lock acquired but not safely unlocked";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiExpression expression = (PsiExpression) location;
        final PsiType type = expression.getType();
        final String text = type.getPresentableText();
        return text + " should be locked in a try block, and unlocked in a finally block #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new LockResourceVisitor();
    }

    private static class LockResourceVisitor extends BaseInspectionVisitor{

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if(!isLockAcquireMethod(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression
                    .getMethodExpression();
            if(methodExpression == null)
            {
                return;
            }
            final PsiExpression lhs = methodExpression.getQualifierExpression();
            if(!(lhs instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiElement referent =
                    ((PsiReference) lhs).resolve();
            if(referent == null || !(referent instanceof PsiVariable)) {
                return;
            }
            final PsiVariable boundVariable = (PsiVariable) referent;

            PsiElement currentContext = expression;
            while(true){
                final PsiTryStatement tryStatement =
                        PsiTreeUtil.getParentOfType(currentContext,
                                                                      PsiTryStatement.class);
                if(tryStatement == null) {
                    registerError(expression);
                    return;
                }
                if(resourceIsOpenedInTryAndClosedInFinally(tryStatement,
                                                           expression,
                                                           boundVariable)) {
                    return;
                }
                currentContext = tryStatement;
            }
        }

        private static boolean resourceIsOpenedInTryAndClosedInFinally(PsiTryStatement tryStatement,
                                                                       PsiExpression lhs,
                                                                       PsiVariable boundVariable){
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if(finallyBlock == null){
                return false;
            }
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if(tryBlock == null){
                return false;
            }
            if(!PsiTreeUtil.isAncestor(tryBlock, lhs, true)){
                return false;
            }
            return containsResourceClose(finallyBlock, boundVariable);
        }

        private static boolean containsResourceClose(PsiCodeBlock finallyBlock,
                                                     PsiVariable boundVariable){
            final CloseVisitor visitor =
                    new CloseVisitor(boundVariable);
            finallyBlock.accept(visitor);
            return visitor.containsStreamClose();
        }
    }

    private static class CloseVisitor extends PsiRecursiveElementVisitor{
        private boolean containsClose = false;
        private PsiVariable objectToClose;

        private CloseVisitor(PsiVariable objectToClose){
            super();
            this.objectToClose = objectToClose;
        }

        public void visitElement(@NotNull PsiElement element){
            if(!containsClose){
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call){
            if(containsClose){
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"unlock".equals(methodName)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(!(qualifier instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement referent =
                    ((PsiReference) qualifier).resolve();
            if(referent == null)
            {
                return;
            }
            if(referent.equals(objectToClose)){
                containsClose = true;
            }
        }

        public boolean containsStreamClose(){
            return containsClose;
        }
    }

    private static boolean isLockAcquireMethod(PsiMethodCallExpression expression){
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if(methodExpression == null) {
            return false;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!"lock".equals(methodName) &&!"lockInterruptibly".equals(methodName)) {
            return false;
        }
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if(qualifier == null) {
            return false;
        }
        return TypeUtils.expressionHasTypeOrSubtype("java.util.concurrent.locks.Lock",
                                                    qualifier);
    }

}
