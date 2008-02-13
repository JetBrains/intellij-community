/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class UnnecessaryFinalOnParameterInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean onlyWarnOnAbstractMethods = false;


    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.final.on.parameter.display.name");
    }

    @NotNull
    public String getID() {
        return "UnnecessaryFinalForMethodParameter";
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiParameter parameter = (PsiParameter)infos[0];
        final String parameterName = parameter.getName();
        return InspectionGadgetsBundle.message(
                "unnecessary.final.on.parameter.problem.descriptor",
                parameterName);
    }


    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "unnecessary.final.on.parameter.only.interface.option"),
                this, "onlyWarnOnAbstractMethods");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryFinalOnParameterVisitor();
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new RemoveModifierFix((String) infos[1]);
    }

    private class UnnecessaryFinalOnParameterVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            for (final PsiParameter parameter : parameters) {
                checkParameter(method, parameter);
            }
        }

        private void checkParameter(PsiMethod method, PsiParameter parameter) {
            if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                if (containingClass.isInterface() ||
                        containingClass.isAnnotationType()) {
                    registerModifierError(PsiModifier.FINAL, parameter,
                            parameter, PsiModifier.FINAL);
                    return;
                }
            }
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                registerModifierError(PsiModifier.FINAL, parameter,
                        parameter, PsiModifier.FINAL);
                return;
            }
            if (onlyWarnOnAbstractMethods) {
                return;
            }
            if (VariableAccessUtils.variableIsUsedInInnerClass(parameter,
                    method)) {
                return;
            }
            registerModifierError(PsiModifier.FINAL, parameter,
                    parameter, PsiModifier.FINAL);
        }
    }
}
