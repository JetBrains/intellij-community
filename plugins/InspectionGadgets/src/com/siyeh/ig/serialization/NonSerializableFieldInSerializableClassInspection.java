/*
 * Copyright 2006 Dave Griffith
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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class NonSerializableFieldInSerializableClassInspection
        extends ClassInspection {

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "non.serializable.field.in.serializable.class.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonSerializableFieldInSerializableClassVisitor();
    }

    private static class NonSerializableFieldInSerializableClassVisitor
            extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            if (field.hasModifierProperty(PsiModifier.TRANSIENT)
                    || field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiClass aClass = field.getContainingClass();
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            if (SerializationUtils.isProbablySerializable(field.getType())) {
                return;
            }
            final boolean hasWriteObject =
                    SerializationUtils.hasWriteObject(aClass);
            if (hasWriteObject) {
                return;
            }
            registerFieldError(field);
        }
    }
}
