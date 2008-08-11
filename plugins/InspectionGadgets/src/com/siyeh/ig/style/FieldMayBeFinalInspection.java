/*
 * Copyright 2008 Bas Leijdekkers
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
import com.siyeh.ig.fixes.MakeFieldFinalFix;
import com.siyeh.ig.psiutils.InitializationUtils;
import org.jetbrains.annotations.NotNull;

public class FieldMayBeFinalInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "field.may.be.final.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "field.may.be.final.problem.descriptor");
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return MakeFieldFinalFix.buildFixUnconditional((PsiField)infos[0]);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new FieldMayBeFinalVisitor();
    }

    private static class FieldMayBeFinalVisitor extends BaseInspectionVisitor {

        @Override
        public void visitField(PsiField field) {
            super.visitField(field);
            if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                if (!staticFieldMayBeFinal(field)) {
                    return;
                }
            } else {
                if (!fieldMayBeFinal(field)) {
                    return;
                }
            }
            registerVariableError(field, field);
        }

        private static boolean fieldMayBeFinal(PsiField field) {
            final PsiExpression intializer = field.getInitializer();
            final PsiClass aClass = field.getContainingClass();
            final PsiClassInitializer[] classInitializers =
                    aClass.getInitializers();
            boolean assignedInInitializer = intializer != null;
            for (PsiClassInitializer classInitializer : classInitializers) {
                if (classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }
                final PsiCodeBlock block = classInitializer.getBody();
                if (InitializationUtils.blockAssignsVariableOrFails(block,
                        field)) {
                    if (assignedInInitializer) {
                        return false;
                    }
                    assignedInInitializer = true;
                }
            }
            final PsiMethod[] methods = aClass.getMethods();
            for (PsiMethod method : methods) {
                if (method.isConstructor() && !assignedInInitializer) {
                    if (!InitializationUtils.methodAssignsVariableOrFails(
                            method, field)) {
                        return false;
                    }
                    continue;
                }
                if (InitializationUtils.methodAssignsVariableOrFails(method,
                        field)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean staticFieldMayBeFinal(PsiField field) {
            final PsiExpression initializer = field.getInitializer();
            final PsiClass aClass = field.getContainingClass();
            final PsiClassInitializer[] classInitializers =
                    aClass.getInitializers();
            boolean assignedInInitializer = initializer != null;
            for (PsiClassInitializer classInitializer : classInitializers) {
                final PsiCodeBlock body = classInitializer.getBody();
                if (classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
                    if (InitializationUtils.blockAssignsVariableOrFails(body,
                            field)) {
                        if (assignedInInitializer) {
                            return false;
                        }
                        assignedInInitializer = true;
                    }
                } else if (InitializationUtils.blockAssignsVariableOrFails(body,
                        field)) {
                    return false;
                }
            }
            if (!assignedInInitializer) {
                return false;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for (PsiMethod method : methods) {
                if (InitializationUtils.methodAssignsVariableOrFails(method,
                        field)) {
                    return false;
                }
            }
            return true;
        }
    }
}
