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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeSerializableFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class NonSerializableWithSerialVersionUIDFieldInspection extends ClassInspection {
    private final MakeSerializableFix fix1 = new MakeSerializableFix();
    private final RemoveSerialVersionUIDFix fix2 = new RemoveSerialVersionUIDFix();

    public String getID(){
        return "NonSerializableClassWithSerialVersionUID";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("non.serializable.with.serialversionuid.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("non.serializable.with.serialversionuid.problem.descriptor");
    }

    protected InspectionGadgetsFix[] buildFixes(PsiElement location){
        return new InspectionGadgetsFix[]{fix1, fix2};
    }

    private static class RemoveSerialVersionUIDFix extends InspectionGadgetsFix{
        public String getName(){
            return InspectionGadgetsBundle.message("non.serializable.with.serialversionuid.remove.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement nameElement = descriptor.getPsiElement();
            final PsiField field = (PsiField)nameElement.getParent();
            field.delete();
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonSerializableWithSerialVersionUIDVisitor();
    }

    private static class NonSerializableWithSerialVersionUIDVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down

            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }

            final PsiField[] fields = aClass.getFields();
            boolean hasSerialVersionUID = false;
            for(final PsiField field : fields){
                if(isSerialVersionUID(field)){
                    hasSerialVersionUID = true;
                }
            }
            if (!hasSerialVersionUID) {
                return;
            }
            if(SerializationUtils.isSerializable(aClass)){
                return;
            }
            registerClassError(aClass);
        }

        private static boolean isSerialVersionUID(PsiField field) {
            final String methodName = field.getName();
            return HardcodedMethodConstants.SERIAL_VERSION_UID.equals(methodName);
        }

    }

}
