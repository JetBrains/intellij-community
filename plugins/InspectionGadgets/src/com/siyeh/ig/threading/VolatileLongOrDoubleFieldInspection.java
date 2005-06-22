package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import org.jetbrains.annotations.NotNull;

public class VolatileLongOrDoubleFieldInspection extends FieldInspection{
    public String getDisplayName(){
        return "Volatile long or double field";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiField field = (PsiField) location.getParent();
        assert field != null;
        final PsiType type = field.getType();
        final String typeString = type.getPresentableText();
        return "Volatile field #ref of type " + typeString + " #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new VolatileLongOrDoubleFieldVisitor();
    }

    private static class VolatileLongOrDoubleFieldVisitor
                                                          extends BaseInspectionVisitor{


        public void visitField(@NotNull PsiField field){
            super.visitField(field);
            if(!field.hasModifierProperty(PsiModifier.VOLATILE)){
                return;
            }
            final PsiType type = field.getType();
            if(PsiType.LONG.equals(type) || PsiType.DOUBLE.equals(type)){
                registerFieldError(field);
            }
        }
    }
}
