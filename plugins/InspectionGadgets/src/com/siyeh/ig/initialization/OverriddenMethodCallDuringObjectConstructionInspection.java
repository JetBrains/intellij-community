/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jetbrains.annotations.NotNull;

public class OverriddenMethodCallDuringObjectConstructionInspection
        extends BaseInspection{

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "overridden.method.call.in.constructor.display.name");
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
            final PsiMember member =
                    PsiTreeUtil.getParentOfType(call, PsiMethod.class,
                            PsiClassInitializer.class);
            if (member instanceof PsiClassInitializer){
                final PsiClassInitializer classInitializer =
                        (PsiClassInitializer)member;
                if (classInitializer.hasModifierProperty(PsiModifier.STATIC)){
                    return;
                }
            } else if (member instanceof PsiMethod){
                final PsiMethod method = (PsiMethod)member;
                if (!isObjectConstructionMethod(method)){
                    return;
                }
            } else {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier != null){
                if(!(qualifier instanceof PsiThisExpression ||
                        qualifier instanceof PsiSuperExpression)){
                    return;
                }
            }
            final PsiClass containingClass = member.getContainingClass();
            if(containingClass == null ||
                    containingClass.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            final PsiMethod calledMethod =
                    (PsiMethod) methodExpression.resolve();
            if(calledMethod == null || !PsiUtil.canBeOverriden(calledMethod)){
                return;
            }
            final PsiClass calledMethodClass =
                    calledMethod.getContainingClass();
            if(!InheritanceUtil.isCorrectDescendant(containingClass,
                    calledMethodClass, true)){
                return;
            }
            if(!MethodUtils.isOverriddenInHierarchy(calledMethod,
                    containingClass)){
                return;
            }
            registerMethodCallError(call);
        }
        
        public static boolean isObjectConstructionMethod(PsiMethod method){
            if(method.isConstructor()){
                return true;
            }
            if(CloneUtils.isClone(method)){
                return true;
            }
            if(MethodUtils.simpleMethodMatches(method, null, "void",
                    "readObject", "java.io.ObjectInputStream")){
                return true;
            }
            return MethodUtils.simpleMethodMatches(method, null, "void",
                    "readObjectNoData");
        }

    }
}