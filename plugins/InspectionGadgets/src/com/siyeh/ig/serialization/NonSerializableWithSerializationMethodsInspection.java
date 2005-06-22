package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeSerializableFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

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
        assert aClass!=null;
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
