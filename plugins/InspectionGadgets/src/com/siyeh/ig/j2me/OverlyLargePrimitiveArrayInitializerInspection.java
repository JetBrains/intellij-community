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
package com.siyeh.ig.j2me;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.*;

public class OverlyLargePrimitiveArrayInitializerInspection
        extends ExpressionInspection{
    /**
     * @noinspection PublicField
     */
    public int m_limit = 64;

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("large.initializer.primitive.type.array.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiExpression expression = (PsiExpression) location;
        final int numElements = calculateNumElements(expression);
        return InspectionGadgetsBundle.message("large.initializer.primitive.type.array.problem.descriptor", numElements);
    }

    public JComponent createOptionsPanel(){
        return new SingleIntegerFieldOptionsPanel(InspectionGadgetsBundle.message("large.initializer.primitive.type.array.maximum.number.of.elements.option"),
                                                  this, "m_limit");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new OverlyLargePrimitiveArrayInitializerVisitor();
    }

    private class OverlyLargePrimitiveArrayInitializerVisitor
            extends BaseInspectionVisitor{


        public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression){
            super.visitArrayInitializerExpression(expression);
            final PsiType type = expression.getType();
            if(type == null) {
                return;
            }
            final PsiType componentType = type.getDeepComponentType();
            if(!(componentType instanceof PsiPrimitiveType)) {
                return;
            }
            final int numElements = calculateNumElements(expression);
            if(numElements <= m_limit) {
                return;
            }
            registerError(expression);
        }
    }

    private int calculateNumElements(PsiExpression expression){
        if(expression instanceof PsiArrayInitializerExpression) {
            final PsiArrayInitializerExpression arrayExpression = (PsiArrayInitializerExpression) expression;
            final PsiExpression[] initializers = arrayExpression.getInitializers();
            int out = 0;
            for(final PsiExpression initializer : initializers){
                out += calculateNumElements(initializer);
            }
            return out;
        }
        return 1;
    }
}
