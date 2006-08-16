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
package com.siyeh.ig.finalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class NoExplicitFinalizeCallsInspection extends ExpressionInspection{

    public String getID(){
        return "FinalizeCalledExplicitly";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "finalize.called.explicitly.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.FINALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "finalize.called.explicitly.problem.descriptor");
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NoExplicitFinalizeCallsVisitor();
    }

    private static class NoExplicitFinalizeCallsVisitor
            extends BaseInspectionVisitor{

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if (!MethodCallUtils.isCallToMethod(expression, null, PsiType.VOID,
                    HardcodedMethodConstants.FINALIZE)) {
                return;
            }
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            if(containingMethod == null){
                return;
            }
            if (MethodUtils.methodMatches(containingMethod, null, PsiType.VOID,
                    HardcodedMethodConstants.FINALIZE)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}