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
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CheckForOutOfMemoryOnLargeArrayAllocationInspection
        extends ExpressionInspection{

    /**
     * @noinspection PublicField
     */
    public int m_limit = 64;

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "large.array.allocation.no.outofmemoryerror.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message(
                "large.array.allocation.no.outofmemoryerror.problem.descriptor");
    }

    public JComponent createOptionsPanel(){
        return new SingleIntegerFieldOptionsPanel(
                InspectionGadgetsBundle.message(
                        "large.array.allocation.no.outofmemoryerror.maximum.number.of.elements.option"),
                this, "m_limit");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new CheckForOutOfMemoryOnLargeArrayAllocationVisitor();
    }

    private class CheckForOutOfMemoryOnLargeArrayAllocationVisitor
            extends BaseInspectionVisitor{

        public void visitNewExpression(@NotNull PsiNewExpression expression){
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if(!(type instanceof PsiArrayType)){
                return;
            }
            int size = 1;
            final PsiExpression[] dimensions = expression.getArrayDimensions();
            for(final PsiExpression dimension : dimensions){
                final Integer intValue =
                        (Integer) ConstantExpressionUtil.computeCastTo(
                                dimension, PsiType.INT);
                if (intValue != null) {
                    size *= intValue.intValue();
                }
            }
            if(size <= m_limit){
                return;
            }
            if(outOfMemoryExceptionCaught(expression)){
                return;
            }
            registerError(expression);
        }

        private boolean outOfMemoryExceptionCaught(PsiElement element){
            PsiElement currentElement = element;
            while(true){
                final PsiTryStatement containingTryStatement =
                        PsiTreeUtil.getParentOfType(currentElement,
                                PsiTryStatement.class);
                if(containingTryStatement == null){
                    return false;
                }
                if(catchesOutOfMemoryException(containingTryStatement)){
                    return true;
                }
                currentElement = containingTryStatement;
            }
        }

        private boolean catchesOutOfMemoryException(PsiTryStatement statement){
            final PsiCatchSection[] sections = statement.getCatchSections();
            for(final PsiCatchSection section : sections){
                final PsiType catchType = section.getCatchType();
                if(catchType != null){
                    final String typeText = catchType.getCanonicalText();
                    if("java.lang.OutOfMemoryError".equals(typeText)){
                        return true;
                    }
                }
            }
            return false;
        }
    }
}