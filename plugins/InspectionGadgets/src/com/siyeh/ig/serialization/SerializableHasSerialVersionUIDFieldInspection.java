/*
 * Copyright 2003-2005 Dave Griffith
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
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddSerialVersionUIDFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SerializableHasSerialVersionUIDFieldInspection extends ClassInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreSerializableDueToInheritance = true;
    private final AddSerialVersionUIDFix fix = new AddSerialVersionUIDFix();

    public String getID(){
        return "serial";
    }
    public String getDisplayName() {
        return "Serializable class without serialVersionUID";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref doesn't define a serialVersionUID field #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore classes serializable due to inheritance",
                this, "m_ignoreSerializableDueToInheritance");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SerializableDefinesSerialVersionUIDVisitor();
    }

    private class SerializableDefinesSerialVersionUIDVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down

            if (aClass.isInterface() || aClass.isAnnotationType()|| aClass.isEnum()) {
                return;
            }
            if(aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass){
                return;
            }
            final PsiField[] fields = aClass.getFields();
            boolean hasSerialVersionUID = false;
            for(final PsiField field : fields){
                if(isSerialVersionUID(field)){
                    hasSerialVersionUID = true;
                }
            }
            if(hasSerialVersionUID){
                return;
            }
            if (m_ignoreSerializableDueToInheritance) {
                if (!SerializationUtils.isDirectlySerializable(aClass)) {
                    return;
                }
            } else {
                if (!SerializationUtils.isSerializable(aClass)) {
                    return;
                }
            }

            registerClassError(aClass);
        }

        private boolean isSerialVersionUID(PsiField field) {
            final String methodName = field.getName();
            return HardcodedMethodConstants.SERIAL_VERSION_UID.equals(methodName);
        }

    }

}
