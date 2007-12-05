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
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryFinalOnLocalVariableInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.final.on.local.variable.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiVariable parameter = (PsiVariable)infos[0];
        final String parameterName = parameter.getName();
        return InspectionGadgetsBundle.message(
                "unnecessary.final.on.local.variable.problem.descriptor",
                parameterName);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryFinalOnLocalVariableVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RemoveModifierFix(location);
    }

    private static class UnnecessaryFinalOnLocalVariableVisitor
            extends BaseInspectionVisitor {

        @Override public void visitDeclarationStatement(
                PsiDeclarationStatement statement) {
            super.visitDeclarationStatement(statement);
            final PsiElement[] declaredElements =
                    statement.getDeclaredElements();
            if (declaredElements.length == 0) {
                return;
            }
            for (final PsiElement declaredElement : declaredElements) {
                if (!(declaredElement instanceof PsiLocalVariable)) {
                    return;
                }
                final PsiLocalVariable variable =
                        (PsiLocalVariable)declaredElement;
                if (!variable.hasModifierProperty(PsiModifier.FINAL)) {
                    return;
                }
            }
            final PsiCodeBlock containingBlock =
                    PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
            if (containingBlock == null) {
                return;
            }
            for (PsiElement declaredElement : declaredElements) {
                final PsiLocalVariable variable =
                        (PsiLocalVariable)declaredElement;
                if (VariableAccessUtils.variableIsUsedInInnerClass(variable,
                        containingBlock)) {
                    return;
                }
            }
            final PsiLocalVariable variable1 =
                    (PsiLocalVariable)statement.getDeclaredElements()[0];
            registerModifierError(PsiModifier.FINAL, variable1, variable1);
        }

        @Override public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCatchSection[] catchSections =
                    statement.getCatchSections();
            for (PsiCatchSection catchSection : catchSections) {
                final PsiParameter parameter = catchSection.getParameter();
                final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
                if (parameter == null || catchBlock == null) {
                    continue;
                }
                if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
                    continue;
                }
                if (!VariableAccessUtils.variableIsUsedInInnerClass(
                        parameter, catchBlock)) {
                    registerModifierError(PsiModifier.FINAL, parameter,
                            parameter);
                }
            }
        }

        @Override public void visitForeachStatement(PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            final PsiParameter parameter = statement.getIterationParameter();
            if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            if (VariableAccessUtils.variableIsUsedInInnerClass(parameter,
                    statement)) {
                return;
            }
            registerModifierError(PsiModifier.FINAL, parameter, parameter);
        }
    }
}