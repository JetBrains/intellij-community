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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class NoopMethodInAbstractClassInspection extends MethodInspection {

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "noop.method.in.abstract.class.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NoopMethodInAbstractClassVisitor();
    }

    private static class NoopMethodInAbstractClassVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (method.isConstructor()) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null) {
                return;
            }
            if (containingClass.isInterface() ||
                    containingClass.isAnnotationType()) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (!MethodUtils.isEmpty(method)) {
                return;
            }
            registerMethodError(method);
        }
    }
}