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
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class OverriddenMethodCallInConstructorInspection
                                                         extends MethodInspection{
    public String getDisplayName(){
        return InspectionGadgetsBundle.message("overridden.method.call.in.constructor.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("overridden.method.call.in.constructor.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new AbstractMethodCallInConstructorVisitor();
    }

    private static class AbstractMethodCallInConstructorVisitor
                                                                extends BaseInspectionVisitor{

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if(method == null){
                return;
            }
            if(!method.isConstructor()){
                return;
            }
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            if(methodExpression.isQualified() &&
                    !(methodExpression.getQualifierExpression() instanceof PsiThisExpression)){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            if(containingClass.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            final PsiMethod calledMethod = (PsiMethod) methodExpression.resolve();
            if(calledMethod == null){
                return;
            }
            if(!isOverridable(calledMethod)){
                return;
            }
            final PsiClass calledMethodClass = calledMethod.getContainingClass();
            if(!InheritanceUtil.isInheritorOrSelf(containingClass,
                                                  calledMethodClass, true)){
                return;
            }
            if(!isOverridden(calledMethod)){
                return;
            }
            registerMethodCallError(call);
        }

        private static boolean isOverridden(PsiMethod method){
            final PsiManager psiManager = method.getManager();
            final Project project = psiManager.getProject();
            final SearchScope globalScope = GlobalSearchScope.allScope(project);
            final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
            final PsiElementProcessor.FindElement<PsiMethod> processor = new PsiElementProcessor.FindElement<PsiMethod>();
            searchHelper.processOverridingMethods(processor, method,
                                                  globalScope, true);
            return processor.getFoundElement() != null;
        }

        private static boolean isOverridable(PsiMethod calledMethod){
            return !calledMethod.isConstructor() &&
                    !calledMethod.hasModifierProperty(PsiModifier.FINAL) &&
                    !calledMethod.hasModifierProperty(PsiModifier.STATIC) &&
                    !calledMethod.hasModifierProperty(PsiModifier.PRIVATE);
        }
    }
}
