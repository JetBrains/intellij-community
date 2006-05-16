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
package com.siyeh.ig.visibility;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.util.Query;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class MethodOverloadsParentMethodInspection extends MethodInspection{

    public String getID(){
        return "MethodOverloadsMethodOfSuperclass";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("method.overloads.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new RenameFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "method.overloads.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MethodOverloadsParentMethodVisitor();
    }

    private static class MethodOverloadsParentMethodVisitor
            extends BaseInspectionVisitor{
        public void visitMethod(@NotNull PsiMethod method){
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.PRIVATE)
                    || method.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }
            if (method.getNameIdentifier() == null) {
                return;
            }
            final Query<MethodSignatureBackedByPsiMethod> superMethodQuery =
                    SuperMethodsSearch.search(method, null, true, false);
            if(superMethodQuery.findFirst() != null){
                return;
            }
            PsiClass ancestorClass = aClass.getSuperClass();
            final Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
            while(ancestorClass != null){
                if(!visitedClasses.add(ancestorClass)){
                    return;
                }
                if(methodOverloads(method, ancestorClass)){
                    registerMethodError(method);
                    return;
                }
                ancestorClass = ancestorClass.getSuperClass();
            }
        }

        private static boolean methodOverloads(PsiMethod meth,
                                               PsiClass ancestorClass){
            if(methodOverrides(meth, ancestorClass)){
                return false;
            }
            final String methName = meth.getName();
            final PsiParameterList parameterList = meth.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiMethod[] methods = ancestorClass
                    .findMethodsByName(methName, false);
            for(final PsiMethod testMethod : methods){
                if(!testMethod.hasModifierProperty(PsiModifier.PRIVATE) &&
                        !testMethod.hasModifierProperty(PsiModifier.STATIC) &&
                        !isOverriddenInClass(testMethod,
                                             meth.getContainingClass())){
                    final PsiParameterList testParameterList = testMethod
                            .getParameterList();
                    final PsiParameter[] testParameters = testParameterList
                            .getParameters();
                    if(testParameters.length == parameters.length &&
                            !parametersAreCompatible(parameters,
                                                     testParameters)){
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean isOverriddenInClass(PsiMethod method,
                                                   PsiClass aClass){
            final PsiMethod[] methods = aClass.getMethods();
            for(PsiMethod testMethod : methods){
                final String testMethodName = testMethod.getName();
                if(testMethodName.equals(method.getName())){
                  final PsiMethod[] superMethods =
                          testMethod.findSuperMethods(true);
                    for(final PsiMethod superMethod : superMethods){
                        if(superMethod .equals(method)){
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static boolean parametersAreCompatible(
                PsiParameter[] parameters, PsiParameter[] testParameters){
            for(int i = 0; i < parameters.length; i++){
                final PsiParameter parameter = parameters[i];
                final PsiType parameterType = parameter.getType();
                final PsiParameter testParameter = testParameters[i];
                final PsiType testParameterType = testParameter.getType();

                if(!parameterType.isAssignableFrom(testParameterType)){
                    return false;
                }
            }
            return true;
        }

        private static boolean methodOverrides(PsiMethod meth,
                                               PsiClass ancestorClass){
          final PsiMethod[] superMethods = meth.findSuperMethods(true);
            for(final PsiMethod superMethod : superMethods){
                if(ancestorClass.equals(superMethod.getContainingClass())){
                    return true;
                }
            }
            return false;
        }
    }
}
