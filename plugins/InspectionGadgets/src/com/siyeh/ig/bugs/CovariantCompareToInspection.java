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
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class CovariantCompareToInspection extends MethodInspection {
    private static final String COMPARE_TO_METHOD_NAME = "compareTo";

    public String getDisplayName() {
        return "Covariant 'compareTo()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref should take Object as its argument #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CovariantCompareToVisitor();
    }

    private static class CovariantCompareToVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            final String name = method.getName();
            if (!COMPARE_TO_METHOD_NAME.equals(name)) {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                return;
            }
            final PsiType argType = parameters[0].getType();
            if (TypeUtils.isJavaLangObject(argType)) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(PsiMethod method1 : methods){
                if(isNonVariantCompareTo(method1)){
                    return;
                }
            }
            final PsiClassType[] implementsListTypes = aClass.getImplementsListTypes();
            for(final PsiClassType implementedType : implementsListTypes){
                final String implementedClassName = implementedType.getClassName();
                if(("java.lang.Comparable".equals(implementedClassName) ||
                        "Comparable".equals(implementedClassName)
                )
                        && implementedType.hasParameters()){
                    return;
                }
            }
            registerMethodError(method);
        }

        private static boolean isNonVariantCompareTo(PsiMethod method) {
            final String methodName = method.getName();
            if (!COMPARE_TO_METHOD_NAME.equals(methodName)) {
                return false;
            }
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return false;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                return false;
            }
            final PsiType argType = parameters[0].getType();
            return TypeUtils.isJavaLangObject(argType);
        }

    }

}
