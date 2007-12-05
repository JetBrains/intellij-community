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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeFieldFinalFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NonFinalFieldOfExceptionInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "non.final.field.of.exception.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "non.final.field.of.exception.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return MakeFieldFinalFix.buildFix(location);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonFinalFieldOfExceptionVisitor();
    }

    private static class NonFinalFieldOfExceptionVisitor
            extends BaseInspectionVisitor {

        @Override public void visitField(PsiField field) {
            super.visitField(field);
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!ClassUtils.isSubclass(containingClass,
                    "java.lang.Exception")) {
                return;
            }
            registerFieldError(field);
        }
    }
}