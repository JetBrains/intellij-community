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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.fixes.EncapsulateVariableFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class PublicFieldInspection extends FieldInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("public.field.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message("public.field.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new EncapsulateVariableFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PublicFieldVisitor();
    }

    private static class PublicFieldVisitor extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                if (field.hasModifierProperty(PsiModifier.STATIC)) {
                    return;
                }
                final PsiType type = field.getType();
                if (ClassUtils.isImmutable(type)) {
                    return;
                }
            }
            registerFieldError(field);
        }
    }
}