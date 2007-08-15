/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExceptionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class IOResourceInspection extends BaseInspection {

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
      public void visitNewExpression(@NotNull PsiNewExpression expression){
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
        final PsiVariable boundVariable;
        if (parent instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
          final PsiExpression lhs = assignment.getLExpression();
          if (!(lhs instanceof PsiReferenceExpression)) {
            return;
          }
          final PsiElement referent = ((PsiReference)lhs).resolve();
          if (!(referent instanceof PsiVariable)) {
            return;
          }
          boundVariable = (PsiVariable)referent;
        }
        else if (parent instanceof PsiLocalVariable) {
          boundVariable = (PsiVariable)parent;
        }
        else if (parent instanceof PsiReturnStatement) {
          return;
        }
        else {
          registerError(expression, expression);
          return;
        }
        final PsiElement containingBlock =
                PsiTreeUtil.getParentOfType(expression, PsiCodeBlock.class);
          if(isArgToResourceCreation(boundVariable, containingBlock)){
              return;
          }
          if (!isResourceClosedInFinally(expression, boundVariable) && !isResourceEscapedFromMethod(boundVariable, expression)) {
            registerError(expression, expression);
          }
      }

      private static boolean isResourceEscapedFromMethod(final PsiVariable boundVariable, final PsiElement context) {
        // poor man dataflow
        PsiMethod method = PsiTreeUtil.getParentOfType(context, PsiMethod.class, true, PsiMember.class);
        if (method == null) return false;
        PsiCodeBlock body = method.getBody();
        if (body == null) return false;
        final boolean[] escaped = new boolean[1];
        PsiElementVisitor visitor =
        new PsiRecursiveElementVisitor(){
          public void visitAnonymousClass(PsiAnonymousClass aClass) {
          }
          public void visitReturnStatement(PsiReturnStatement statement) {
            PsiExpression value = statement.getReturnValue();
            value = PsiUtil.deparenthesizeExpression(value);
            if (value instanceof PsiReferenceExpression && ((PsiReferenceExpression)value).resolve() == boundVariable) {
              escaped[0] = true;
            }
          }
        };
        body.accept(visitor);
        return escaped[0];
      }

      private static boolean isResourceClosedInFinally(PsiNewExpression expression,
                                                       PsiVariable boundVariable) {
          PsiElement currentContext = expression;
          while (true) {
              final PsiTryStatement tryStatement = PsiTreeUtil
                  .getParentOfType(currentContext, PsiTryStatement.class,
                                   true, PsiMember.class);
              if (tryStatement == null) {
                  break;
              }
              if (resourceIsClosedInFinally(tryStatement, boundVariable)) {
                  return true;
              }
              currentContext = tryStatement;
          }

          // look for try block down the control flow
          currentContext = PsiTreeUtil.getParentOfType(expression,
                                                       PsiStatement.class,
                                                       false,
                                                       PsiMember.class);
          if (currentContext == null) return false;
          currentContext = currentContext.getNextSibling();
          while (currentContext != null) {
              if (currentContext instanceof PsiTryStatement) {
                  if (resourceIsClosedInFinally(
                      (PsiTryStatement)currentContext, boundVariable)) {
                      return true;
                  }
              }
              Set<PsiType> thrown =
                  ExceptionUtils.calculateExceptionsThrown(currentContext);
              // any thrown exception can interrupt control flow and resource would remain unclosed
              if (!thrown.isEmpty()) return false;
              currentContext = currentContext.getNextSibling();
          }
          return false;
      }

        private static boolean isArgToResourceCreation(PsiVariable boundVariable,
                                                       PsiElement scope){
            final UsedAsIOResourceArgVisitor visitor =
                    new UsedAsIOResourceArgVisitor(boundVariable);
            scope.accept(visitor);
            return visitor.usedAsArgToResourceCreation();
        }

        private static boolean resourceIsClosedInFinally(PsiTryStatement tryStatement,
                                                         PsiVariable boundVariable){
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if(finallyBlock == null){
                return false;
            }
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if(tryBlock == null){
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

        public void visitElement(@NotNull PsiElement element){
            if(!containsStreamClose){
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            if(containsStreamClose){
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!HardcodedMethodConstants.CLOSE.equals(methodName)) {
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

        public void visitNewExpression(@NotNull PsiNewExpression expression){
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
            if(expressions.length == 0){
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

    public static boolean isIOResource(PsiNewExpression expression){
        return isNonTrivialInputStream(expression) ||
                isNonTrivialWriter(expression) ||
                isNonTrivialReader(expression) ||
                TypeUtils.expressionHasTypeOrSubtype(expression,
		                "java.io.RandomAccessFile") ||
                isNonTrivialOutputStream(expression);
    }

    private static boolean isNonTrivialOutputStream(PsiNewExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype(expression,
		        "java.io.OutputStream")
                &&
                !TypeUtils.expressionHasTypeOrSubtype(expression,
		                "java.io.ByteArrayOutputStream");
    }

	private static boolean isNonTrivialReader(PsiNewExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype(expression,
		        "java.io.Reader") &&
                !TypeUtils.expressionHasTypeOrSubtype(expression,
		                "java.io.CharArrayReader", "java.io.StringReader");
    }

    private static boolean isNonTrivialWriter(PsiNewExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype(expression,
		        "java.io.Writer") &&
                !TypeUtils.expressionHasTypeOrSubtype(expression,
		                "java.io.CharArrayWriter", "java.io.StringWriter");
    }

    private static boolean isNonTrivialInputStream(PsiNewExpression expression){
        return TypeUtils.expressionHasTypeOrSubtype(expression,
		        "java.io.InputStream") &&
                !TypeUtils.expressionHasTypeOrSubtype(expression,
		                "java.io.ByteArrayInputStream",
		                "java.io.StringBufferInputStream");
    }
}