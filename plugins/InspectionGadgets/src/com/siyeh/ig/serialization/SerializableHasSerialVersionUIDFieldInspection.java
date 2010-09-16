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
package com.siyeh.ig.serialization;

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddSerialVersionUIDFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class SerializableHasSerialVersionUIDFieldInspection
        extends SerializableInspection {

    @Override
    @NotNull
    public String getID() {
        return "serial";
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "serializable.class.without.serialversionuid.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "serializable.class.without.serialversionuid.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new AddSerialVersionUIDFix();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SerializableHasSerialVersionUIDFieldVisitor();
    }

    private class SerializableHasSerialVersionUIDFieldVisitor
            extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()||
                    aClass.isEnum()) {
                return;
            }
            if (aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiEnumConstantInitializer) {
                return;
            }
            final PsiField serialVersionUIDField = aClass.findFieldByName(
                    HardcodedMethodConstants.SERIAL_VERSION_UID, false);
            if (serialVersionUIDField != null) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            if (isIgnoredSubclass(aClass)) {
                return;
            }
            registerClassError(aClass);
        }
    }
}
