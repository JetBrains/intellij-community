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
package com.siyeh.ig.visibility;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InnerClassVariableHidesOuterClassVariableInspection
        extends FieldInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreInvisibleFields = true;

    @NotNull
    public String getID(){
        return "InnerClassFieldHidesOuterClassField";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "inner.class.field.hides.outer.display.name");
    }

    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "inner.class.field.hides.outer.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new RenameFix();
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "inner.class.field.hides.outer.ignore.option"),
                this, "m_ignoreInvisibleFields");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InnerClassVariableHidesOuterClassVariableVisitor();
    }

    private class InnerClassVariableHidesOuterClassVariableVisitor
            extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String fieldName = field.getName();
            if (HardcodedMethodConstants.SERIAL_VERSION_UID.equals(fieldName)) {
                return;    //special case
            }
            boolean reportStaticsOnly = false;
            if(aClass.hasModifierProperty(PsiModifier.STATIC)) {
                reportStaticsOnly = true;
            }
            PsiClass ancestorClass =
                    ClassUtils.getContainingClass(aClass);
            while (ancestorClass != null) {
                final PsiField ancestorField =
                        ancestorClass.findFieldByName(fieldName, false);
                if (ancestorField != null) {
                    if (!m_ignoreInvisibleFields ||
                            !reportStaticsOnly ||
                            field.hasModifierProperty(PsiModifier.STATIC)) {
                        registerFieldError(field);
                    }
                }
                ancestorClass = ClassUtils.getContainingClass(ancestorClass);
            }
        }
    }
}