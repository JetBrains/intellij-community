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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class OverriddenMethodCallInConstructorInspection
        extends MethodInspection{

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "overridden.method.call.in.constructor.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
      return InspectionGadgetsBundle.message(
              "overridden.method.call.in.constructor.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new OverriddenMethodCallInConstructorVisitor();
    }

    private static class OverriddenMethodCallInConstructorVisitor
            extends BaseInspectionVisitor{

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if (method == null || !method.isConstructor()) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if(qualifierExpression != null) {
                if(!(qualifierExpression instanceof PsiThisExpression ||
                        qualifierExpression instanceof PsiSuperExpression)){
                    return;
                }
            }
            final PsiClass constructorClass = method.getContainingClass();
            if (constructorClass == null ||
                    constructorClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiMethod calledMethod =
                    (PsiMethod) methodExpression.resolve();
            if (calledMethod == null || !PsiUtil.canBeOverriden(calledMethod)) {
                return;
            }
            final PsiClass calledMethodClass =
                    calledMethod.getContainingClass();
            if(!InheritanceUtil.isCorrectDescendant(calledMethodClass,
                    constructorClass, true)){
                return;
            }
            if(!MethodUtils.isOverridden(calledMethod)){
                return;
            }
            registerMethodCallError(call);
        }
    }
}