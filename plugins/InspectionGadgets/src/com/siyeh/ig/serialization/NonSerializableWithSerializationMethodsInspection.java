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
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeSerializableFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class NonSerializableWithSerializationMethodsInspection
        extends ClassInspection{
    private final MakeSerializableFix fix = new MakeSerializableFix();

    public String getID(){
        return "NonSerializableClassWithSerializationMethods";
    }
    public String getDisplayName(){
        return InspectionGadgetsBundle.message("non.serializable.class.with.readwriteobject.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public String buildErrorString(PsiElement location){
        final PsiClass aClass = (PsiClass) location.getParent();
        assert aClass!=null;
        final boolean hasReadObject = SerializationUtils.hasReadObject(aClass);
        final boolean hasWriteObject =
                SerializationUtils.hasWriteObject(aClass);

        if(hasReadObject && hasWriteObject){
            return InspectionGadgetsBundle.message("non.serializable.class.with.readwriteobject.problem.descriptor.both");
        } else if(hasWriteObject){
            return InspectionGadgetsBundle.message("non.serializable.class.with.readwriteobject.problem.descriptor.write");
        } else{
            return InspectionGadgetsBundle.message("non.serializable.class.with.readwriteobject.problem.descriptor.read");
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NonserializableDefinesSerializationMethodsVisitor();
    }

    private static class NonserializableDefinesSerializationMethodsVisitor
            extends BaseInspectionVisitor{

        public void visitClass(@NotNull PsiClass aClass){
            // no call to super, so it doesn't drill down
            if(aClass.isInterface() || aClass.isAnnotationType()){
                return;
            }

            final boolean hasReadObject =
                    SerializationUtils.hasReadObject(aClass);
            final boolean hasWriteObject =
                    SerializationUtils.hasWriteObject(aClass);
            if(!hasWriteObject && !hasReadObject){
                return;
            }
            if(SerializationUtils.isSerializable(aClass)){
                return;
            }
            registerClassError(aClass);
        }
    }
}
