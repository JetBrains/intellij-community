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

public class CallToSimpleSetterInClassInspection extends ExpressionInspection{
    private final InlineCallFix fix = new InlineCallFix();

    public String getID(){
        return "CallToSimpleSetterFromWithinClass";
    }

    public String getDisplayName(){
        return "Call to simple setter from within class";
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to simple setter '#ref()' from within class #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class InlineCallFix extends InspectionGadgetsFix{
        public String getName(){
            return "Inline call to setter";
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
            final PsiExpressionList argList = call.getArgumentList();
            assert argList != null;
            final PsiExpression[] args = argList.getExpressions();
            final PsiExpression arg = args[0];
            final PsiMethod method = call.resolveMethod();
            assert method != null;
            final PsiCodeBlock body = method.getBody();
            final PsiStatement[] statements = body.getStatements();
            final PsiExpressionStatement assignmentStatemnt = (PsiExpressionStatement) statements[0];
            final PsiAssignmentExpression assignment = (PsiAssignmentExpression) assignmentStatemnt
                    .getExpression();

            final PsiExpression qualifier = methodExpression
                    .getQualifierExpression();
            final PsiExpression lhs = assignment.getLExpression();
            final String lhsText = lhs.getText();
            if(qualifier == null){
                final String newExpression;
                if(lhsText.startsWith("this.")){
                    newExpression = lhsText + " = " + arg.getText();
                } else{
                    newExpression = "this." + lhsText + " = " + arg.getText();
                }
                replaceExpression(call, newExpression);
            } else{
                final String newExpression;
                if(lhsText.startsWith("this.")){
                    newExpression = qualifier.getText() + '.' + lhsText.substring(5) +
                            " = " + arg.getText();
                } else{
                     newExpression = qualifier.getText() + '.' + lhsText +
                            " = " + arg.getText();
                }
                replaceExpression(call,
                                  newExpression);
            }
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new CallToSimpleSetterInClassVisitor();
    }

    private class CallToSimpleSetterInClassVisitor
            extends BaseInspectionVisitor{
        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);

            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            assert methodName != null;
            if(!methodName.startsWith("set")){
                return;
            }
            final PsiExpressionList argList = call.getArgumentList();
            if(argList == null){
                return;
            }
            final PsiExpression[] args = argList.getExpressions();
            if(args == null || args.length != 1){
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
            if(!isSimpleSetter(method)){
                return;
            }
            registerMethodCallError(call);
        }
    }

    private boolean isSimpleSetter(PsiMethod method){
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
        if(!(statement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpressionStatement possibleAssignmentStatement =
                (PsiExpressionStatement) statement;
        final PsiExpression possibleAssignment =
                possibleAssignmentStatement.getExpression();
        if(possibleAssignment == null){
            return false;
        }
        if(!(possibleAssignment instanceof PsiAssignmentExpression)){
            return false;
        }
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression) possibleAssignment;
        final PsiJavaToken sign = assignment.getOperationSign();
        if(!sign.getTokenType().equals(JavaTokenType.EQ)){
            return false;
        }
        final PsiExpression lhs = assignment.getLExpression();
        if(!(lhs instanceof PsiReferenceExpression)){
            return false;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
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
        if(!field.getContainingClass().equals(method.getContainingClass())){
            return false;
        }

        final PsiExpression rhs = assignment.getRExpression();
        if(!(rhs instanceof PsiReferenceExpression)){
            return false;
        }
        final PsiReferenceExpression rReference = (PsiReferenceExpression) rhs;
        final PsiExpression rQualifier = rReference.getQualifierExpression();
        if(rQualifier != null){
            return false;
        }
        final PsiElement rReferent = rReference.resolve();
        if(rReferent == null){
            return false;
        }
        if(!(rReferent instanceof PsiParameter)){
            return false;
        }
        final PsiType fieldType = field.getType();
        final PsiType parameterType = ((PsiVariable) rReferent).getType();
        if(fieldType == null || parameterType == null){
            return false;
        }
        return fieldType.getCanonicalText()
                .equals(parameterType.getCanonicalText());
    }
}
