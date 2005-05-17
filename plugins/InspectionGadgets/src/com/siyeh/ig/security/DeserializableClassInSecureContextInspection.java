package com.siyeh.ig.security;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class DeserializableClassInSecureContextInspection extends ClassInspection {

    public String getDisplayName() {
        return "Deserializable class in secure context";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref may be deserialized, compromising security #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DeserializableClassInSecureContextVisitor();
    }

    private static class DeserializableClassInSecureContextVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(final PsiMethod method : methods){
                if(SerializationUtils.isReadObject(method)){
                    if(ControlFlowUtils.methodAlwaysThrowsException(method)){
                        return;
                    }
                }
            }
            registerClassError(aClass);
        }
    }
}
