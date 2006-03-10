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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class StringConcatenationInsideStringBufferAppendInspection
        extends ExpressionInspection{

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "string.concatenation.inside.string.buffer.append.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new StringConcatenationInsideStringBufferAppendVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new ReplaceWithChainedAppendFix();
    }

    private static class ReplaceWithChainedAppendFix
            extends InspectionGadgetsFix{

        public String getName(){
            return InspectionGadgetsBundle.message(
                    "string.concatenation.inside.string.buffer.append.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement methodNameElement = descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    (PsiReferenceExpression) methodNameElement.getParent();
            if (methodExpression == null) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) methodExpression.getParent();
            if (methodCallExpression == null) {
                return;
            }
            final PsiExpressionList argList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            final PsiExpression arg = args[0];
            final List<String> expressions = findConcatenationComponents(arg);
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            @NonNls final StringBuffer newExpressionBuffer = new StringBuffer();
            newExpressionBuffer.append(qualifier.getText());
            for(String expression : expressions){
                newExpressionBuffer.append(".append(");
                newExpressionBuffer.append(expression);
                newExpressionBuffer.append(')');
            }
            final String newExpression = newExpressionBuffer.toString();
            replaceExpression(methodCallExpression, newExpression);
        }

        private static List<String> findConcatenationComponents(
                PsiExpression concatenation) throws IncorrectOperationException {
            final List<String> out = new ArrayList<String>();
            findConcatenationComponents(concatenation, out);
            return out;
        }

        private static void findConcatenationComponents(
                PsiExpression concatenation, List<String> out)
                throws IncorrectOperationException {
            if(concatenation instanceof PsiBinaryExpression){
                final PsiType type = concatenation.getType();
                if (type != null && type.equalsToText("java.lang.String")){
                    PsiBinaryExpression binaryExpression =
                            (PsiBinaryExpression) concatenation.copy();
                    PsiExpression lhs = binaryExpression.getLOperand();
                    PsiExpression rhs = binaryExpression.getROperand();
                    assert rhs != null;
                    if (!PsiUtil.isConstantExpression(rhs)) {
                        findConcatenationComponents(lhs, out);
                        findConcatenationComponents(rhs, out);
                        return;
                    }
                    final StringBuffer builder =
                            new StringBuffer(rhs.getText());
                    while (lhs instanceof PsiBinaryExpression) {
                        binaryExpression = (PsiBinaryExpression)lhs;
                        rhs = binaryExpression.getROperand();
                        assert rhs != null;
                        if (!PsiUtil.isConstantExpression(rhs)) {
                            findConcatenationComponents(lhs, out);
                            out.add(builder.toString());
                            return;
                        }
                        lhs = binaryExpression.getLOperand();
                        builder.insert(0, " + ");
                        builder.insert(0, rhs.getText());
                    }
                    if (PsiUtil.isConstantExpression(lhs)) {
                        builder.insert(0, " + ");
                        builder.insert(0, lhs.getText());
                        out.add(builder.toString());
                    } else {
                        findConcatenationComponents(lhs, out);
                        out.add(builder.toString());
                    }
                } else {
                    out.add(concatenation.getText());
                }
            } else if(concatenation instanceof PsiParenthesizedExpression){
                final PsiExpression expression = ((PsiParenthesizedExpression)
                        concatenation).getExpression();
                if (expression != null) {
                    out.add(expression.getText());
                }
            } else{
                out.add(concatenation.getText());
            }
        }
    }

    private static class StringConcatenationInsideStringBufferAppendVisitor
            extends BaseInspectionVisitor{
        public void visitMethodCallExpression(
                PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression
                    .getMethodExpression();
            @NonNls final String methodName = methodExpression.getReferenceName();
            if(!"append".equals(methodName)){
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if(args.length != 1){
                return;
            }
            final PsiExpression arg = args[0];
            if(!isConcatenation(arg)){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            final String className = containingClass.getQualifiedName();
            if(!"java.lang.StringBuffer".equals(className) &&
               !"java.lang.StringBuilder".equals(className)){
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isConcatenation(PsiExpression arg){
            if(!(arg instanceof PsiBinaryExpression)){
                return false;
            }
            if (PsiUtil.isConstantExpression(arg)) {
               return false;
            }
            final PsiType type = arg.getType();
            if(type == null){
                return false;
            }
            return type.equalsToText("java.lang.String");
        }
    }
}