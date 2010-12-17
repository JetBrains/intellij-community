/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OverloadedMethodsWithSameNumberOfParametersInspection
        extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreLibraryOverrides = true;

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "overloaded.methods.with.same.number.parameters.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "overloaded.methods.with.same.number.parameters.problem.descriptor");
    }

    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "overloaded.methods.with.same.number.parameters.option"),
                this, "ignoreLibraryOverrides");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new OverloadedMethodsWithSameNumberOfParametersVisitor();
    }

    private class OverloadedMethodsWithSameNumberOfParametersVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            if (method.isConstructor()) {
                return;
            }
            if (method.getNameIdentifier() == null) {
                return;
            }
            final int parameterCount = calculateParameterCount(method);
            if (parameterCount == 0) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            if (ignoreLibraryOverrides) {
                final PsiMethod[] superMethods = method.findSuperMethods();
                for (PsiMethod superMethod : superMethods) {
                    if (superMethod instanceof PsiCompiledElement) {
                        return;
                    }
                }
            }
            final String methodName = method.getName();
            final PsiMethod[] sameNameMethods =
                    aClass.findMethodsByName(methodName, true);
            for (PsiMethod sameNameMethod : sameNameMethods) {
                if (sameNameMethod.equals(method)) {
                    continue;
                }
                final int testParameterCount =
                        calculateParameterCount(sameNameMethod);
                if (parameterCount == testParameterCount) {
                    registerMethodError(method);
                    return;
                }
            }
        }

        private int calculateParameterCount(PsiMethod method) {
            final PsiParameterList parameterList = method.getParameterList();
            return parameterList.getParametersCount();
        }
    }
}