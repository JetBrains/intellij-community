/*
 * Copyright 2006-2007 Bas Leijdekkers
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class AnonymousClassVariableHidesContainingMethodVariableInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "anonymous.class.variable.hides.containing.method.variable.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final Object info = infos[0];
        if (info instanceof PsiParameter) {
            return InspectionGadgetsBundle.message(
                    "anonymous.class.parameter.hides.containing.method.variable.problem.descriptor");
        } else if (info instanceof PsiField) {
            return InspectionGadgetsBundle.message(
                    "anonymous.class.field.hides.containing.method.variable.problem.descriptor");
        }
        return InspectionGadgetsBundle.message(
                "anonymous.class.variable.hides.containing.method.variable.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new RenameFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AnonymousClassVariableHidesOuterClassVariableVisitor();
    }
}