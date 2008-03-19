/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JDBCResourceInspection extends BaseInspection{

    private static final String[] creationMethodClassName =
            new String[]{
                "java.sql.Driver",
                "java.sql.DriverManager",
                "javax.sql.DataSource",
                "java.sql.Connection",
                "java.sql.Connection",
                "java.sql.Connection",
                "java.sql.Statement",
                "java.sql.Statement",
                "java.sql.Statement",
            };
    @NonNls private static final String[] creationMethodName =
            new String[]{
                "connect",
                "getConnection",
                "getConnection",
                "createStatement",
                "prepareStatement",
                "prepareCall",
                "executeQuery",
                "getResultSet",
                "getGeneratedKeys"
            };

    /**
     * @noinspection StaticCollection
     */
    private static final Set<String> creationMethodNameSet =
            new HashSet<String>(9);

    static{
        creationMethodNameSet.addAll(Arrays.asList(creationMethodName));
    }

    @NotNull
    public String getID(){
        return "JDBCResourceOpenedButNotSafelyClosed";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "jdbc.resource.opened.not.closed.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final PsiExpression expression = (PsiExpression) infos[0];
        final PsiType type = expression.getType();
        assert type != null;
        final String text = type.getPresentableText();
        return InspectionGadgetsBundle.message(
                "jdbc.resource.opened.not.closed.problem.descriptor", text);
    }

    public BaseInspectionVisitor buildVisitor(){
        return new JDBCResourceVisitor();
    }

    private static class JDBCResourceVisitor extends BaseInspectionVisitor{

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if(!isJDBCResourceCreation(expression)){
                return;
            }
            final PsiElement parent = expression.getParent();
            final PsiAssignmentExpression assignment;
            if(!(parent instanceof PsiAssignmentExpression)){
                if(!(parent instanceof PsiTypeCastExpression)){
                    registerError(expression, expression);
                    return;
                }
                final PsiElement grandParent = parent.getParent();
                if(!(grandParent instanceof PsiAssignmentExpression)){
                    registerError(expression, expression);
                    return;
                }
                assignment = (PsiAssignmentExpression) grandParent;
            }else{
                assignment = (PsiAssignmentExpression) parent;
            }
            final PsiExpression lhs = assignment.getLExpression();
            if(!(lhs instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement referent = ((PsiReference) lhs).resolve();
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
                if(resourceIsOpenedInTryAndClosedInFinally(
                        tryStatement, expression, boundVariable)){
                    return;
                }
                currentContext = tryStatement;
            }
        }

        private static boolean resourceIsOpenedInTryAndClosedInFinally(
                PsiTryStatement tryStatement,
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
            final ResourceCloseVisitor visitor =
                    new ResourceCloseVisitor(boundVariable);
            finallyBlock.accept(visitor);
            return visitor.containsResourceClose();
        }

        private static boolean isJDBCResourceCreation(
                PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if(name == null){
                return false;
            }
            if(!creationMethodNameSet.contains(name)){
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return false;
            }
            for(int i = 0; i < creationMethodName.length; i++){
                if(!name.equals(creationMethodName[i])){
                    continue;
                }
                final PsiClass containingClass = method.getContainingClass();
                if(containingClass == null){
                    return false;
                }
                final String className = containingClass.getQualifiedName();
                if(className == null){
                    return false;
                }
                if(className.equals(creationMethodClassName[i])){
                    return true;
                }
            }
            return false;
        }
    }

    private static class ResourceCloseVisitor
            extends JavaRecursiveElementVisitor{
        private boolean containsResourceClose = false;
        private PsiVariable streamToClose;

        private ResourceCloseVisitor(PsiVariable streamToClose){
            super();
            this.streamToClose = streamToClose;
        }

        @Override public void visitElement(@NotNull PsiElement element){
            if(!containsResourceClose){
                super.visitElement(element);
            }
        }

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            if(containsResourceClose){
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
            if(referent.equals(streamToClose)){
                containsResourceClose = true;
            }
        }

        public boolean containsResourceClose(){
            return containsResourceClose;
        }
    }
}
