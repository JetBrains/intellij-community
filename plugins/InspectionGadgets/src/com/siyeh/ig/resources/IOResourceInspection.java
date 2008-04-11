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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class IOResourceInspection extends ResourceInspection {

    @NotNull
    public String getID(){
        return "IOResourceOpenedButNotSafelyClosed";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "i.o.resource.opened.not.closed.display.name");
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
        return new IOResourceVisitor();
    }

    private static class IOResourceVisitor extends BaseInspectionVisitor{

        @Override public void visitNewExpression(
                @NotNull PsiNewExpression expression){
            super.visitNewExpression(expression);
            if(!isIOResource(expression)){
                return;
            }
            final PsiElement parent = getExpressionParent(expression);
            if(parent instanceof PsiReturnStatement){
                return;
            } if (parent instanceof PsiExpressionList){
                PsiElement grandParent = parent.getParent();
                if(grandParent instanceof PsiAnonymousClass){
                    grandParent = grandParent.getParent();
                }
                if(grandParent instanceof PsiNewExpression &&
                        isIOResource((PsiNewExpression) grandParent)){
                    return;
                }
            }
            final PsiVariable boundVariable = getVariable(parent);
            final PsiElement containingBlock =
                    PsiTreeUtil.getParentOfType(expression, PsiCodeBlock.class);
            if(isArgumentOfResourceCreation(boundVariable, containingBlock)){
                return;
            }
            if (isSafelyClosed(boundVariable, expression)) {
                return;
            }
            if (isResourceEscapedFromMethod(boundVariable, expression)) {
                return;
            }
            registerError(expression, expression);
        }

    }

    public static boolean isIOResource(PsiExpression expression){
        return isNonTrivialInputStream(expression) ||
                isNonTrivialWriter(expression) ||
                isNonTrivialReader(expression) ||
                TypeUtils.expressionHasTypeOrSubtype(expression,
		                "java.io.RandomAccessFile") ||
                isNonTrivialOutputStream(expression);
    }

    private static boolean isNonTrivialOutputStream(PsiExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype(expression,
                "java.io.OutputStream") &&
                !TypeUtils.expressionHasTypeOrSubtype(expression,
                "java.io.ByteArrayOutputStream");
    }

    private static boolean isNonTrivialReader(PsiExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype(expression,
		        "java.io.Reader") &&
                !TypeUtils.expressionHasTypeOrSubtype(expression,
		                "java.io.CharArrayReader", "java.io.StringReader");
    }

    private static boolean isNonTrivialWriter(PsiExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype(expression,
		        "java.io.Writer") &&
                !TypeUtils.expressionHasTypeOrSubtype(expression,
		                "java.io.CharArrayWriter", "java.io.StringWriter");
    }

    private static boolean isNonTrivialInputStream(PsiExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype(expression,
		        "java.io.InputStream") &&
                !TypeUtils.expressionHasTypeOrSubtype(expression,
		                "java.io.ByteArrayInputStream",
		                "java.io.StringBufferInputStream");
    }

    private static boolean isArgumentOfResourceCreation(
            PsiVariable boundVariable, PsiElement scope){
        final UsedAsIOResourceArgumentVisitor visitor =
                new UsedAsIOResourceArgumentVisitor(boundVariable);
        scope.accept(visitor);
        return visitor.usedAsArgToResourceCreation();
    }

    private static class UsedAsIOResourceArgumentVisitor
            extends JavaRecursiveElementVisitor{

        private boolean usedAsArgToResourceCreation = false;
        private PsiVariable ioResource;

        private UsedAsIOResourceArgumentVisitor(PsiVariable ioResource){
            super();
            this.ioResource = ioResource;
        }

        @Override public void visitNewExpression(
                @NotNull PsiNewExpression expression){
            if(usedAsArgToResourceCreation){
                return;
            }
            super.visitNewExpression(expression);
            if(!isIOResource(expression)){
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null){
                return;
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            if(arguments.length == 0){
                return;
            }
            final PsiExpression argument = arguments[0];
            if(argument == null ||
                    !(argument instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement referent =
                    ((PsiReference) argument).resolve();
            if(referent == null || !referent.equals(ioResource)){
                return;
            }
            usedAsArgToResourceCreation = true;
        }

        public boolean usedAsArgToResourceCreation(){
            return usedAsArgToResourceCreation;
        }
    }
}