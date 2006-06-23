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
package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class DollarSignInNameInspection extends BaseInspection {

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new RenameFix();
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "dollar.sign.in.name.problem.descriptor");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DollarSignInNameVisitor();
    }

    private static class DollarSignInNameVisitor extends BaseInspectionVisitor {

        public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final String name = variable.getName();
            if (name == null) {
                return;
            }
            if (name.indexOf((int)'$') < 0) {
                return;
            }
            registerVariableError(variable);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            final String name = method.getName();
            if (name.indexOf((int)'$') < 0) {
                return;
            }
            registerMethodError(method);
        }

        public void visitClass(@NotNull PsiClass aClass) {
            //note: no call to super, to avoid drill-down
            final String name = aClass.getName();
            if (name == null) {
                return;
            }
            if (name.indexOf((int)'$') < 0) {
                return;
            }
            registerClassError(aClass);
        }
    }
}