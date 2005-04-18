package com.siyeh.ig.resources;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;

public class IOResourceInspection extends ExpressionInspection{
    public String getID(){
        return "IOResourceOpenedButNotSafelyClosed";
    }

    public String getDisplayName(){
        return "I/O resource opened but not safely closed";
    }

    public String getGroupDisplayName(){
        return GroupNames.RESOURCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiExpression expression = (PsiExpression) location;
        final PsiType type = expression.getType();
        final String text = type.getPresentableText();
        return text +
                       " should be opened in a try block, and closed in a finally block #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new IOResourceVisitor(this, inspectionManager, onTheFly);
    }

    private static class IOResourceVisitor extends BaseInspectionVisitor{
        private IOResourceVisitor(BaseInspection inspection,
                                  InspectionManager inspectionManager,
                                  boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(PsiNewExpression expression){
            super.visitNewExpression(expression);
            if(!isIOResource(expression)){
                return;
            }
            final PsiElement parent = expression.getParent();
            if(parent instanceof PsiExpressionList){
                final PsiElement grandParent = parent.getParent();
                if(grandParent instanceof PsiNewExpression &&
                                isIOResource((PsiNewExpression) grandParent)){
                    return;
                }
            }
            if(!(parent instanceof PsiAssignmentExpression)){
                registerError(expression);
                return;
            }
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression) parent;
            final PsiExpression lhs = assignment.getLExpression();
            if(!(lhs instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement referent =
                    ((PsiReference) lhs).resolve();
            if(referent == null || !(referent instanceof PsiVariable)){
                return;
            }
            final PsiVariable boundVariable = (PsiVariable) referent;
            final PsiElement containingBlock =
                    PsiTreeUtil.getParentOfType(expression, PsiCodeBlock.class);

            if(isArgToResourceCreation(boundVariable, containingBlock)){
                return;
            }
            PsiElement currentContext = expression;
            while(true){
                final PsiTryStatement tryStatement =
                        (PsiTryStatement) PsiTreeUtil.getParentOfType(currentContext,
                                                                      PsiTryStatement.class);
                if(tryStatement == null){
                    registerError(expression);
                    return;
                }
                if(resourceIsOpenedInTryAndClosedInFinally(tryStatement,
                                                           expression,
                                                           boundVariable)){
                    return;
                }
                currentContext = tryStatement;
            }
        }

        private static boolean isArgToResourceCreation(PsiVariable boundVariable,
                                                       PsiElement scope){
            final UsedAsIOResourceArgVisitor visitor =
                    new UsedAsIOResourceArgVisitor(boundVariable);
            scope.accept(visitor);
            return visitor.usedAsArgToResourceCreation();
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
            final StreamCloseVisitor visitor =
                    new StreamCloseVisitor(boundVariable);
            finallyBlock.accept(visitor);
            return visitor.containsStreamClose();
        }
    }

    private static class StreamCloseVisitor extends PsiRecursiveElementVisitor{
        private boolean containsStreamClose = false;
        private PsiVariable streamToClose;

        private StreamCloseVisitor(PsiVariable streamToClose){
            super();
            this.streamToClose = streamToClose;
        }

        public void visitElement(PsiElement element){
            if(!containsStreamClose){
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call){
            if(containsStreamClose){
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"close".equals(methodName)){
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
            if(referent.equals(streamToClose)){
                containsStreamClose = true;
            }
        }

        public boolean containsStreamClose(){
            return containsStreamClose;
        }
    }

    private static class UsedAsIOResourceArgVisitor
            extends PsiRecursiveElementVisitor{
        private boolean usedAsArgToResourceCreation = false;
        private PsiVariable ioResource;

        private UsedAsIOResourceArgVisitor(PsiVariable ioResource){
            super();
            this.ioResource = ioResource;
        }

        public void visitNewExpression(PsiNewExpression expression){
            if(usedAsArgToResourceCreation){
                return;
            }
            super.visitNewExpression(expression);
            if(!isIOResource(expression)){
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            if(argList == null){
                return;
            }
            final PsiExpression[] expressions = argList.getExpressions();
            if(expressions == null || expressions.length == 0){
                return;
            }
            final PsiExpression arg = expressions[0];
            if(arg == null || !(arg instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement referent =
                    ((PsiReference) arg).resolve();
            if(referent == null || !referent.equals(ioResource)){
                return;
            }
            usedAsArgToResourceCreation = true;
        }

        public boolean usedAsArgToResourceCreation(){
            return usedAsArgToResourceCreation;
        }
    }

    private static boolean isIOResource(PsiNewExpression expression){
        return isNonTrivialInputStream(expression) ||
                       isNonTrivialWriter(expression) ||
                       isNonTrivialReader(expression) ||
                       TypeUtils.expressionHasTypeOrSubtype("java.io.RandomAccessFile",
                                                            expression) ||
                       isNonTrivialOutputStream(expression);
    }

    private static boolean isNonTrivialOutputStream(PsiNewExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype("java.io.OutputStream",
                                                    expression)
                                                                      &&
                                                                      !TypeUtils.expressionHasTypeOrSubtype("java.io.ByteArrayOutputStream",
                                                                                                            expression);
    }

    private static boolean isNonTrivialReader(PsiNewExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype("java.io.Reader",
                                                    expression)
                                                                      &&
                                                                      !TypeUtils.expressionHasTypeOrSubtype("java.io.CharArrayReader",
                                                                                                            expression)
                                                                      &&
                                                                      !TypeUtils.expressionHasTypeOrSubtype("java.io.StringReader",
                                                                                                            expression);
    }

    private static boolean isNonTrivialWriter(PsiNewExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype("java.io.Writer",
                                                    expression)
                                                                      &&
                                                                      !TypeUtils.expressionHasTypeOrSubtype("java.io.CharArrayWriter",
                                                                                                            expression)
                                                                      &&
                                                                      !TypeUtils.expressionHasTypeOrSubtype("java.io.StringWriter",
                                                                                                            expression);
    }

    private static boolean isNonTrivialInputStream(PsiNewExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype("java.io.InputStream",
                                                    expression)
                                                                      &&
                                                                      !TypeUtils.expressionHasTypeOrSubtype("java.io.ByteArrayInputStream",
                                                                                                            expression)
                                                                      &&
                                                                      !TypeUtils.expressionHasTypeOrSubtype("java.io.StringBufferInputStream",
                                                                                                            expression);
    }
}
