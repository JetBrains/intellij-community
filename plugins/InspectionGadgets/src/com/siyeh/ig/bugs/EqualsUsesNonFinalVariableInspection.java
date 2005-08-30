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
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class EqualsUsesNonFinalVariableInspection extends ExpressionInspection{
    public String getID(){
        return "NonFinalFieldReferenceInEquals";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("non.final.field.in.equals.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("non.final.field.in.equals.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new EqualsUsesNonFinalVariableVisitor();
    }

    private static class EqualsUsesNonFinalVariableVisitor
            extends BaseInspectionVisitor{
        private boolean m_inEquals = false;

        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression){
            super.visitReferenceExpression(expression);
            if(!m_inEquals){
                return;
            }
            final PsiElement element = expression.resolve();
            if(!(element instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) element;
            if(field.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            registerError(expression);
        }

        public void visitMethod(@NotNull PsiMethod method){
            final boolean isEquals = MethodUtils.isEquals(method);
            if(isEquals){
                m_inEquals = true;
            }
            super.visitMethod(method);
            if(isEquals){
                m_inEquals = false;
            }
        }

    }
}
