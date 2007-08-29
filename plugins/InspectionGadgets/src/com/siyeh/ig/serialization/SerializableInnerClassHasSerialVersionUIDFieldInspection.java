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
package com.siyeh.ig.serialization;

import com.intellij.psi.PsiElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddSerialVersionUIDFix;
import org.jetbrains.annotations.NotNull;

public class SerializableInnerClassHasSerialVersionUIDFieldInspection
        extends SerializableInspection {

    @NotNull
    public String getID() {
        return "SerializableNonStaticInnerClassWithoutSerialVersionUID";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "serializable.inner.class.has.serial.version.uid.field.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "serializable.inner.class.has.serial.version.uid.field.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new AddSerialVersionUIDFix();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SerializableInnerClassHasSerialVersionUIDFieldVisitor(this);
    }

}