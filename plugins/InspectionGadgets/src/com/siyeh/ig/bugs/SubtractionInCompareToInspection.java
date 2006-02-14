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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class SubtractionInCompareToInspection extends ExpressionInspection{

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "subtraction.in.compareto.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message(
                "subtraction.in.compareto.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new SubtractionInCompareToVisitor();
    }

    private static class SubtractionInCompareToVisitor
                                                       extends BaseInspectionVisitor{

        public void visitBinaryExpression(@NotNull PsiBinaryExpression exp){
            super.visitBinaryExpression(exp);
            if(!(exp.getROperand() != null)){
                return;
            }
            if(!isSubtraction(exp)){
                return;
            }
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(exp, PsiMethod.class);
            if(!MethodUtils.isCompareTo(method)){
                return;
            }
            registerError(exp);
        }

        private static boolean isSubtraction(PsiBinaryExpression exp){
            final PsiExpression rhs = exp.getROperand();
            if(rhs == null){
                return false;
            }
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return tokenType.equals(JavaTokenType.MINUS);
        }
    }

}
