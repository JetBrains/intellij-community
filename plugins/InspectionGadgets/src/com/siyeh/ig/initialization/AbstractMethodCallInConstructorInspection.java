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
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class AbstractMethodCallInConstructorInspection extends MethodInspection{

    public String getGroupDisplayName(){
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "abstract.method.call.in.constructor.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new AbstractMethodCallInConstructorVisitor();
    }

    private static class AbstractMethodCallInConstructorVisitor
            extends BaseInspectionVisitor{

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if(method == null){
                return;
            }
            if(!method.isConstructor()){
                return;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier != null) {
                if (!(qualifier instanceof PsiThisExpression) &&
                        !(qualifier instanceof PsiSuperExpression)) {
                    return;
                }
            }
            final PsiMethod calledMethod =
                    (PsiMethod) methodExpression.resolve();
            if(calledMethod == null){
                return;
            }
            if(calledMethod.isConstructor()){
                return;
            }
            if(!calledMethod.hasModifierProperty(PsiModifier.ABSTRACT)){
                return;
            }
            final PsiClass calledMethodClass =
                    calledMethod.getContainingClass();
            if(calledMethodClass == null){
                return;
            }
            final PsiClass methodClass = method.getContainingClass();
            if(!calledMethodClass.equals(methodClass)){
                return;
            }
            registerMethodCallError(call);
        }
    }
}