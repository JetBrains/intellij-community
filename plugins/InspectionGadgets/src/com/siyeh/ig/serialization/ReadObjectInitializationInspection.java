package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.InitializationUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class ReadObjectInitializationInspection extends ClassInspection {
    public String getID(){
        return "InstanceVariableMayNotBeInitializedByReadObject";
    }
    public String getDisplayName() {
        return "Instance variable may not be initialized by 'readObject()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Instance variable #ref may not be initialized during call to readObject #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ReadObjectInitializationVisitor();
    }

    private static class ReadObjectInitializationVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // no call to super, so it doesn't drill down
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null)
            {
                return;
            }
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }

            if (!SerializationUtils.isReadObject(method)) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            for(final PsiField field : fields){
                if(!isFieldInitialized(field, method)){
                    registerFieldError(field);
                }
            }

        }

        public static boolean isFieldInitialized(PsiField field, PsiMethod method) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
            if (field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) {
                return true;
            }
            final PsiCodeBlock body = method.getBody();
            return InitializationUtils.blockMustAssignVariableOrFail(field, body);
        }

    }
}
