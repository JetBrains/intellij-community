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
package com.siyeh.ig.bugs;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.RecursionUtils;
import org.jetbrains.annotations.NotNull;

public class InfiniteRecursionInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "infinite.recursion.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "infinite.recursion.problem.descriptor");
    }

    @Override
    public boolean isEnabledByDefault(){
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new InfiniteRecursionVisitor();
    }

    private static class InfiniteRecursionVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (!RecursionUtils.methodMayRecurse(method)) {
                return;
            }
            if (!RecursionUtils.methodDefinitelyRecurses(method)) {
                return;
            }
            registerMethodError(method);
        }
    }
}