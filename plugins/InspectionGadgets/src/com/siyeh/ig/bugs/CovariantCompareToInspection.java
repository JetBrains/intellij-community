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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.openapi.project.Project;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class CovariantCompareToInspection extends MethodInspection {

  public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "covariant.compareto.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "covariant.compareto.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CovariantCompareToVisitor();
    }

    private static class CovariantCompareToVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            final String name = method.getName();
            if (!HardcodedMethodConstants.COMPARE_TO.equals(name)) {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                return;
            }
            final PsiType paramType = parameters[0].getType();
            if (TypeUtils.isJavaLangObject(paramType)) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final PsiMethod[] methods = aClass.findMethodsByName(
                    HardcodedMethodConstants.COMPARE_TO, false);
            for(PsiMethod compareToMethod : methods){
                if(isNonVariantCompareTo(compareToMethod)){
                    return;
                }
            }
            final PsiManager manager = method.getManager();
            final PsiClass comparableClass =
                    manager.findClass("java.lang.Comparable",
                            method.getResolveScope());
            if (comparableClass != null &&
                    comparableClass.getTypeParameters().length == 1) {
                final PsiSubstitutor superSubstitutor =
                        TypeConversionUtil.getClassSubstitutor(comparableClass,
                                aClass, PsiSubstitutor.EMPTY);
                //null iff aClass is not inheritor of comparableClass
                if (superSubstitutor != null) {
                    final PsiType substituted =
                            superSubstitutor.substitute(
                                    comparableClass.getTypeParameters()[0]);
                    if (paramType.equals(substituted)) {
                        return;
                    }
                }
            }
            registerMethodError(method);
        }

        private static boolean isNonVariantCompareTo(PsiMethod method) {
            final PsiManager manager = method.getManager();
            final Project project = method.getProject();
            final PsiClassType objectType = PsiType.getJavaLangObject(
                    manager, GlobalSearchScope.allScope(project));
            return MethodUtils.methodMatches(method, null, PsiType.INT,
                    HardcodedMethodConstants.COMPARE_TO, objectType);
        }
    }
}