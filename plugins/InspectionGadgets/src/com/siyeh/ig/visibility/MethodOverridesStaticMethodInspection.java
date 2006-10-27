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
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class MethodOverridesStaticMethodInspection extends MethodInspection{

    public String getID(){
        return "MethodOverridesStaticMethodOfSuperclass";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "method.overrides.static.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "method.overrides.static.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new RenameFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MethodOverridesStaticMethodVisitor();
    }

    private static class MethodOverridesStaticMethodVisitor
            extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null){
                return;
            }
            if (method.getNameIdentifier() == null) {
                return;
            }
            final String methodName = method.getName();
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters == null){
                return;
            }
            final int numParameters = parameters.length;
            PsiClass ancestorClass = aClass.getSuperClass();
            final Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
            while(ancestorClass != null){
                if(!visitedClasses.add(ancestorClass)){
                    return;
                }
                final PsiMethod[] methods =
                        ancestorClass.findMethodsByName(methodName, false);
                for(final PsiMethod testMethod : methods){
                    final PsiParameterList testParametersList =
                            testMethod.getParameterList();
                  final int numTestParameters = testParametersList.getParametersCount();
                    if(numParameters != numTestParameters){
                        continue;
                    }
                    if(testMethod.hasModifierProperty(PsiModifier.STATIC) &&
                            !testMethod.hasModifierProperty(PsiModifier.PRIVATE)){
                        registerMethodError(method);
                        return;
                    }
                }
                ancestorClass = ancestorClass.getSuperClass();
            }
        }
    }
}