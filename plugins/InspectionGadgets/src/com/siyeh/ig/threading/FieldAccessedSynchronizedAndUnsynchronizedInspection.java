package com.siyeh.ig.threading;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FieldAccessedSynchronizedAndUnsynchronizedInspection extends ClassInspection {
    public String getDisplayName() {
        return "Field accessed in both synchronized and unsynchronized contexts";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    protected String buildErrorString(PsiElement location) {
        return "Field #ref is accessed in both synchronized and unsynchronized contexts #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FieldAccessedSynchronizedAndUnsynchronizedVisitor();
    }

    private static class FieldAccessedSynchronizedAndUnsynchronizedVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            final VariableAccessVisitor visitor = new VariableAccessVisitor();
            aClass.accept(visitor);
            final Set<PsiElement> fields = visitor.getInappropriatelyAccessedFields();
            for(Object field1 : fields){
                final PsiField field = (PsiField) field1;
                if(!field.hasModifierProperty(PsiModifier.FINAL)){
                    final PsiClass containingClass = field.getContainingClass();
                    if(aClass.equals(containingClass)){
                        registerFieldError(field);
                    }
                }
            }
        }

    }

}
