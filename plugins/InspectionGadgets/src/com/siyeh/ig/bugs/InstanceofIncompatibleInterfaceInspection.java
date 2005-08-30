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
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class InstanceofIncompatibleInterfaceInspection
        extends ExpressionInspection{
    public String getDisplayName(){
        return InspectionGadgetsBundle.message("instanceof.with.incompatible.interface.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("instanceof.with.incompatible.interface.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new InstanceofIncompatibleInterfaceVisitor();
    }

    private static class InstanceofIncompatibleInterfaceVisitor
            extends BaseInspectionVisitor{


        public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression){
            super.visitInstanceOfExpression(expression);

            final PsiTypeElement castTypeElement = expression.getCheckType();
            if(castTypeElement == null){
                return;
            }
            final PsiType castType = castTypeElement.getType();
            if(!(castType instanceof PsiClassType)){
                return;
            }
            final PsiClass castClass = ((PsiClassType) castType).resolve();
            if(castClass == null){
                return;
            }
            if(!castClass.isInterface()){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            final PsiType operandType = operand.getType();
            if(!(operandType instanceof PsiClassType)){
                return;
            }
            final PsiClass operandClass =
                    ((PsiClassType) operandType).resolve();
            if(operandClass == null){
                return;
            }
            if(InheritanceUtil.existsMutualSubclass(operandClass, castClass)){
                return;
            }
            registerError(castTypeElement);
        }
    }

}
