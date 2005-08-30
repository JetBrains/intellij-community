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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class AssignmentToCollectionFieldFromParameterInspection
        extends ExpressionInspection{
    public String getID(){
        return "AssignmentToCollectionOrArrayFieldFromParameter";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("assignment.collection.array.field.from.parameter.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiAssignmentExpression assignment = (PsiAssignmentExpression) location.getParent();
        assert assignment != null;
        final PsiExpression lhs = assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        assert rhs != null;
        final PsiElement element = ((PsiReference) lhs).resolve();

        final PsiField field = (PsiField) element;
        assert field != null;
        final PsiType type = field.getType();
        if(type.getArrayDimensions() > 0){
            return InspectionGadgetsBundle
              .message("assignment.collection.array.field.from.parameter.problem.descriptor.array", rhs.getText());
        } else{
            return InspectionGadgetsBundle
              .message("assignment.collection.array.field.from.parameter.problem.descriptor.collection", rhs.getText());
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new AssignmentToCollectionFieldFromParameterVisitor();
    }

    private static class AssignmentToCollectionFieldFromParameterVisitor
            extends BaseInspectionVisitor{

        public void visitAssignmentExpression(@NotNull
                PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();

            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.EQ)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            if(!CollectionUtils.isArrayOrCollectionField(lhs)){
                return;
            }
            final PsiExpression rhs = expression.getRExpression();
            if(!(rhs instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement element = ((PsiReference) rhs).resolve();
            if(!(element instanceof PsiParameter)){
                return;
            }
            if(!(element.getParent() instanceof PsiParameterList)){
                return;
            }
            registerError(lhs);
        }
    }

}
