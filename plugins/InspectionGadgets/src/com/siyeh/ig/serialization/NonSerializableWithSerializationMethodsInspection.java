package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MakeSerializableFix;
import com.siyeh.ig.psiutils.SerializationUtils;

public class NonSerializableWithSerializationMethodsInspection
        extends ClassInspection{
    private final MakeSerializableFix fix = new MakeSerializableFix();

    public String getID(){
        return "NonSerializableClassWithSerializationMethods";
    }
    public String getDisplayName(){
        return "Non-serializable class with 'readObject()' or 'writeObject()'";
    }

    public String getGroupDisplayName(){
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public String buildErrorString(PsiElement location){
        final PsiClass aClass = (PsiClass) location.getParent();
        final boolean hasReadObject = SerializationUtils.hasReadObject(aClass);
        final boolean hasWriteObject =
                SerializationUtils.hasWriteObject(aClass);

        if(hasReadObject && hasWriteObject){
            return "Non-serializable class #ref defines readObject() and writeObject() #loc";
        } else if(hasWriteObject){
            return "Non-serializable class #ref defines writeObject() #loc";
        } else{
            return "Non-serializable class #ref defines readObject() #loc";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new NonserializableDefinesSerializationMethodsVisitor(this,
                                                                     inspectionManager,
                                                                     onTheFly);
    }

    private static class NonserializableDefinesSerializationMethodsVisitor
            extends BaseInspectionVisitor{
        private NonserializableDefinesSerializationMethodsVisitor(BaseInspection inspection,
                                                                  InspectionManager inspectionManager,
                                                                  boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass){
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
