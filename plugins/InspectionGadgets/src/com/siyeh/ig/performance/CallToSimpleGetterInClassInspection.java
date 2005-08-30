/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class CallToSimpleGetterInClassInspection extends ExpressionInspection{
    private final InlineCallFix fix = new InlineCallFix();

    public String getID(){
        return "CallToSimpleGetterFromWithinClass";
    }

    public String getDisplayName(){
        return "Call to simple getter from within class";
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to simple getter '#ref()' from within class #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class InlineCallFix extends InspectionGadgetsFix{
        public String getName(){
            return "Inline call to getter";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement methodIdentifier = descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    (PsiReferenceExpression) methodIdentifier.getParent();
            assert methodExpression != null;
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) methodExpression.getParent();
            assert call != null;
            final PsiMethod method = call.resolveMethod();
            assert method != null;
            final PsiCodeBlock body = method.getBody();
            final PsiStatement[] statements = body.getStatements();
            final PsiReturnStatement returnStatement = (PsiReturnStatement) statements[0];
            final PsiExpression returnValue = returnStatement.getReturnValue();
            final String returnValueText = returnValue.getText();
            final PsiExpression qualifier = methodExpression
                    .getQualifierExpression();
            if(qualifier == null){
                if(returnValueText.startsWith("this.")){
                    replaceExpression(call, returnValueText);
                } else{
                    replaceExpression(call, "this." + returnValueText);
                }
            } else{
                if(returnValueText.startsWith("this.")){
                    replaceExpression(call,
                                      qualifier.getText() + '.' + returnValueText.substring(5));
                }
               else{
                    replaceExpression(call,
                                      qualifier.getText() + '.' + returnValueText);
                }
            }
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new CallToSimpleGetterInClassVisitor();
    }

    private class CallToSimpleGetterInClassVisitor
            extends BaseInspectionVisitor{
        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call
                    .getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(methodName == null){
                return;
            }
            if(!methodName.startsWith("get") && !methodName.startsWith("is")){
                return;
            }
            final PsiExpressionList argList = call.getArgumentList();
            if(argList == null){
                return;
            }

            final PsiExpression[] args = argList.getExpressions();
            if(args == null || args.length != 0){
                return;
            }
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(call);
            if(containingClass == null){
                return;
            }
            final PsiMethod method = call.resolveMethod();
            if(method == null){
                return;
            }
            if(!containingClass.equals(method.getContainingClass())){
                return;
            }
            if(!isSimpleGetter(method)){
                return;
            }
            registerMethodCallError(call);
        }
    }

    private boolean isSimpleGetter(PsiMethod method){
        if(method.hasModifierProperty(PsiModifier.SYNCHRONIZED)){
            return false;
        }
        final PsiCodeBlock body = method.getBody();
        if(body == null){
            return false;
        }
        final PsiStatement[] statements = body.getStatements();
        if(statements.length != 1){
            return false;
        }
        final PsiStatement statement = statements[0];
        if(!(statement instanceof PsiReturnStatement)){
            return false;
        }
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) statement;
        final PsiExpression value = returnStatement.getReturnValue();
        if(value == null){
            return false;
        }
        if(!(value instanceof PsiReferenceExpression)){
            return false;
        }

        final PsiReferenceExpression reference = (PsiReferenceExpression) value;
        final PsiExpression qualifier = reference.getQualifierExpression();
        if(qualifier != null && !PsiKeyword.THIS.equals(qualifier.getText())){
            return false;
        }
        final PsiElement referent = reference.resolve();
        if(referent == null){
            return false;
        }
        if(!(referent instanceof PsiField)){
            return false;
        }
        final PsiField field = (PsiField) referent;
        final PsiType fieldType = field.getType();
        final PsiType returnType = method.getReturnType();
        if(fieldType == null || returnType == null){
            return false;
        }
        if(!fieldType.getCanonicalText().equals(returnType.getCanonicalText())){
            return false;
        }
        return field.getContainingClass().equals(method.getContainingClass());
    }
}
