package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import org.jetbrains.annotations.NotNull;

public class JavaLangReflectInspection extends VariableInspection {

    public String getDisplayName() {
        return "Use of java.lang.reflect";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Use of type #ref from java.lang.reflect #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new JavaLangReflectVisitor();
    }

    private static class JavaLangReflectVisitor extends BaseInspectionVisitor {
     
        public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            final PsiType componentType = type.getDeepComponentType();
            if(!(componentType instanceof PsiClassType))
            {
                return;
            }
            final String className = ((PsiClassType) componentType).getClassName();
            if (!className.startsWith("java.lang.reflect.")) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

    }

}
