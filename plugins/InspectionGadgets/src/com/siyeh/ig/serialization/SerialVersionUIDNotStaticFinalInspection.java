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
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class SerialVersionUIDNotStaticFinalInspection extends ClassInspection {

    public String getID(){
        return "SerialVersionUIDWithWrongSignature";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "serialversionuid.private.static.final.long.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "serialversionuid.private.static.final.long.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SerialVersionUIDNotStaticFinalVisitor();
    }

    private static class SerialVersionUIDNotStaticFinalVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            PsiField badSerialVersionUIDField = null;
            final PsiField[] fields = aClass.getFields();
            for(final PsiField field : fields){
                if(isSerialVersionUID(field)){
                    if(!field.hasModifierProperty(PsiModifier.STATIC) ||
                            !field.hasModifierProperty(PsiModifier.PRIVATE) ||
                            !field.hasModifierProperty(PsiModifier.FINAL)){
                        badSerialVersionUIDField = field;
                        break;
                    } else{
                        final PsiType type = field.getType();
                        if(!PsiType.LONG.equals(type)){
                            badSerialVersionUIDField = field;
                            break;
                        }
                    }
                }
            }
            if(badSerialVersionUIDField == null) {
                return;
            }
            if(!SerializationUtils.isSerializable(aClass)){
                return;
            }
            registerFieldError(badSerialVersionUIDField);
        }

        private static boolean isSerialVersionUID(PsiField field) {
            final String fieldName = field.getName();
            return HardcodedMethodConstants.SERIAL_VERSION_UID.equals(fieldName);
        }
    }
}