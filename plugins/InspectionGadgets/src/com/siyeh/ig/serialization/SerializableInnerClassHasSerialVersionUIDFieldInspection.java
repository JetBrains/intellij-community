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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddSerialVersionUIDFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SerializableInnerClassHasSerialVersionUIDFieldInspection extends ClassInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreSerializableDueToInheritance = true;
    private final AddSerialVersionUIDFix fix = new AddSerialVersionUIDFix();

    public String getID(){
        return "SerializableNonStaticInnerClassWithoutSerialVersionUID";
    }
    public String getDisplayName() {
        return "Serializable non-static inner class without 'serialVersionUID'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Inner class #ref doesn't define a serialVersionUID field #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
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

            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if(hasSerialVersionUIDField(aClass)){
                return;
            }
            final PsiClass containingClass = aClass.getContainingClass();
            if(containingClass==null)
            {
                return;
            }
            if(aClass.hasModifierProperty(PsiModifier.STATIC))
            {
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

        private boolean hasSerialVersionUIDField(PsiClass aClass) {
            final PsiField[] fields = aClass.getFields();
            boolean hasSerialVersionUID = false;
            for(PsiField field : fields){
                final String fieldName = field.getName();
                if(HardcodedMethodConstants.SERIAL_VERSION_UID.equals(fieldName)) {
                  hasSerialVersionUID = true;
                }
            }
            return hasSerialVersionUID;
        }

    }

}
