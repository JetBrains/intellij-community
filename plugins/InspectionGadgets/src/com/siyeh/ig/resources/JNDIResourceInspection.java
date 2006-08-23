/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.resources;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class JNDIResourceInspection extends ExpressionInspection{

    public String getID(){
        return "JNDIResourceOpenedButNotSafelyClosed";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "jndi.resource.opened.not.closed.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.RESOURCE_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final PsiExpression expression = (PsiExpression) infos[0];
        final PsiType type = expression.getType();
        assert type != null;
        final String text = type.getPresentableText();
        return InspectionGadgetsBundle.message(
                "resource.opened.not.closed.problem.descriptor", text);
    }

    public BaseInspectionVisitor buildVisitor(){
        return new JNDIResourceVisitor();
    }

    private static class JNDIResourceVisitor extends BaseInspectionVisitor{

        @NonNls private static final String LIST = "list";
        @NonNls private static final String LIST_BINDING = "listBindings";

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if(!isJNDIFactoryMethod(expression)){
                return;
            }
            final PsiElement parent = expression.getParent();
            if(!(parent instanceof PsiAssignmentExpression)){
                registerError(expression, expression);
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

            PsiElement currentContext = expression;
            while(true){
                final PsiTryStatement tryStatement =
                        PsiTreeUtil.getParentOfType(currentContext,
                                PsiTryStatement.class);
                if(tryStatement == null){
                    registerError(expression, expression);
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


        public void visitNewExpression(@NotNull PsiNewExpression expression){
            super.visitNewExpression(expression);
            if(!isJNDIResource(expression)){
                return;
            }
            if(expression.getType() == null){
                return;
            }
            final PsiElement parent = expression.getParent();
            if(!(parent instanceof PsiAssignmentExpression)){
                registerError(expression, expression);
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

            PsiElement currentContext = expression;
            while(true){
                final PsiTryStatement tryStatement =
                        PsiTreeUtil.getParentOfType(currentContext,
                                PsiTryStatement.class);
                if(tryStatement == null){
                    registerError(expression, expression);
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

        private static boolean resourceIsOpenedInTryAndClosedInFinally(
                PsiTryStatement tryStatement, PsiExpression lhs,
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

        private static boolean isJNDIResource(PsiNewExpression expression){
            return TypeUtils.expressionHasTypeOrSubtype(
                    "javax.naming.InitialContext", expression);
        }

        private static boolean isJNDIFactoryMethod(
                PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!(LIST.equals(methodName) || LIST_BINDING.equals(methodName))){
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier == null){
                return false;
            }
            return TypeUtils.expressionHasTypeOrSubtype("javax.naming.Context",
                    qualifier);
        }
    }

    private static class CloseVisitor extends PsiRecursiveElementVisitor{

        private boolean containsClose = false;
        private PsiVariable socketToClose;

        private CloseVisitor(PsiVariable socketToClose){
            super();
            this.socketToClose = socketToClose;
        }

        public void visitElement(@NotNull PsiElement element){
            if(!containsClose){
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            if(containsClose){
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!HardcodedMethodConstants.CLOSE.equals(methodName)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(!(qualifier instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement referent =
                    ((PsiReference) qualifier).resolve();
            if(referent == null){
                return;
            }
            if(referent.equals(socketToClose)){
                containsClose = true;
            }
        }

        public boolean containsStreamClose(){
            return containsClose;
        }
    }
}