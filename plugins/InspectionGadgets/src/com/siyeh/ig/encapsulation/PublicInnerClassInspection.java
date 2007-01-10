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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.fixes.MoveClassFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class PublicInnerClassInspection extends ClassInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreEnums = false;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "public.inner.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "public.inner.class.problem.descriptor");
    }


    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "public.inner.class.ignore.enum.option"), this, "ignoreEnums");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new MoveClassFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PublicInnerClassVisitor();
    }

    private class PublicInnerClassVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            if (!ClassUtils.isInnerClass(aClass)) {
                return;
            }
            if (ignoreEnums && aClass.isEnum()) {
                return;
            }
            registerClassError(aClass);
        }
    }
}