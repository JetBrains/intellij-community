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
package com.siyeh.ig.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class OverloadedMethodsWithSameNumberOfParametersInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "overloaded.methods.with.same.number.parameters.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "overloaded.methods.with.same.number.parameters.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OverloadedMethodsWithSameNumberOfParametersVisitor();
    }

    private static class OverloadedMethodsWithSameNumberOfParametersVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            if (method.isConstructor()) {
                return;
            }
            if (method.getNameIdentifier() == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String methodName = method.getName();
            final PsiMethod[] sameNameMethods =
                    aClass.findMethodsByName(methodName, false);
            final int parameterCount = calculateParamCount(method);
            for (PsiMethod sameNameMethod : sameNameMethods) {
                if(!sameNameMethod.equals(method)) {
                    final int testParameterCount =
                            calculateParamCount(sameNameMethod);
                    if(parameterCount == testParameterCount) {
                        registerMethodError(method);
                    }
                }
            }
        }

        private static int calculateParamCount(PsiMethod method) {
            final PsiParameterList parameterList = method.getParameterList();
            return parameterList.getParametersCount();
        }
    }
}