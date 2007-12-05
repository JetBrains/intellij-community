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
package com.siyeh.ig.cloneable;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeCloneableFix;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jetbrains.annotations.NotNull;

public class CloneInNonCloneableClassInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "clone.method.in.non.cloneable.class.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "clone.method.in.non.cloneable.class.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new MakeCloneableFix();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CloneInNonCloneableClassVisitor();
    }

    private static class CloneInNonCloneableClassVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method){
            if (!CloneUtils.isClone(method)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null ||
                    CloneUtils.isCloneable(containingClass)) {
                return;
            }
            registerMethodError(method);
        }
    }
}