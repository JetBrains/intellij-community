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
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class SerializableInnerClassWithNonSerializableOuterClassInspection
        extends ClassInspection {

    /** @noinspection PublicField */
    public boolean m_ignoreSerializableDueToInheritance = true;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "serializable.inner.class.with.non.serializable.outer.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "serializable.inner.class.with.non.serializable.outer.class.ignore.option"),
                this, "m_ignoreSerializableDueToInheritance");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "serializable.inner.class.with.non.serializable.outer.class.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SerializableDefinesSerialVersionUIDVisitor();
    }

    private class SerializableDefinesSerialVersionUIDVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down

            if (aClass.isInterface() || aClass.isAnnotationType() ||
                    aClass.isEnum()) {
                return;
            }
            final PsiClass containingClass = aClass.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (m_ignoreSerializableDueToInheritance) {
                if (!SerializationUtils.isDirectlySerializable(aClass)) {
                    return;
                }
            } else if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            if (SerializationUtils.isSerializable(containingClass)) {
                return;
            }
            registerClassError(aClass);
        }
    }
}