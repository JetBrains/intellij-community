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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class AssignmentToStaticFieldFromInstanceMethodInspection
        extends ExpressionInspection{

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "assignment.to.static.field.from.instance.method.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.ASSIGNMENT_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "assignment.to.static.field.from.instance.method.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new AssignmentToStaticFieldFromInstanceMethod();
    }

    private static class AssignmentToStaticFieldFromInstanceMethod
            extends BaseInspectionVisitor{

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression expression){
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForStaticFieldAccess(lhs);
        }

        public void visitPrefixExpression(
                @NotNull PsiPrefixExpression expression){
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                       !tokenType.equals(JavaTokenType.MINUSMINUS)){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            checkForStaticFieldAccess(operand);
        }

        public void visitPostfixExpression(
                @NotNull PsiPostfixExpression expression){
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                       !tokenType.equals(JavaTokenType.MINUSMINUS)){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            checkForStaticFieldAccess(operand);
        }

        private void checkForStaticFieldAccess(PsiExpression expression){
            if(!(expression instanceof PsiReferenceExpression)){
                return;
            }
            if (isInStaticMethod(expression)) {
                return;
            }
            final PsiElement referent = ((PsiReference) expression).resolve();
            if(referent == null){
                return;
            }
            if(!(referent instanceof PsiField)){
                return;
            }
            final PsiField fieldReferenced = (PsiField) referent;
            if(fieldReferenced.hasModifierProperty(PsiModifier.STATIC)){
                registerError(expression);
            }
        }

        private static boolean isInStaticMethod(PsiElement element) {
            final PsiMember member =
                    PsiTreeUtil.getParentOfType(element,
                            PsiMethod.class, PsiClassInitializer.class);
            if (member == null) {
                return false;
            }
            return member.hasModifierProperty(PsiModifier.STATIC);
        }
    }
}
