package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FieldAccessedSynchronizedAndUnsynchronizedInspection
        extends ClassInspection{
    public String getDisplayName(){
        return "Field accessed in both synchronized and unsynchronized contexts";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    protected String buildErrorString(PsiElement location){
        return "Field #ref is accessed in both synchronized and unsynchronized contexts #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new FieldAccessedSynchronizedAndUnsynchronizedVisitor();
    }

    private static class FieldAccessedSynchronizedAndUnsynchronizedVisitor
            extends BaseInspectionVisitor{
        public void visitClass(@NotNull PsiClass aClass){
            if(!containsSynchronization(aClass)){
                return;
            }
            final VariableAccessVisitor visitor = new VariableAccessVisitor(aClass);
            aClass.accept(visitor);
            final Set<PsiField> fields =
                    visitor.getInappropriatelyAccessedFields();
            for(final PsiField field  : fields){
                if(!field.hasModifierProperty(PsiModifier.FINAL)){
                    final PsiClass containingClass = field.getContainingClass();
                    if(aClass.equals(containingClass)){
                        registerFieldError(field);
                    }
                }
            }
        }
    }

    private static boolean containsSynchronization(PsiClass aClass){
        final ContainsSynchronizationVisitor visitor = new ContainsSynchronizationVisitor();
        aClass.accept(visitor);
        return visitor.containsSynchronization();
    }
}
