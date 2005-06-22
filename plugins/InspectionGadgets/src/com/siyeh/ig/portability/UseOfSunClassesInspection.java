package com.siyeh.ig.portability;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import org.jetbrains.annotations.NotNull;

public class UseOfSunClassesInspection extends VariableInspection {

    public String getDisplayName() {
        return "Use of sun.* classes";
    }

    public String getGroupDisplayName() {
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Use of Sun-supplied class #ref is non-portable #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObsoleteCollectionVisitor();
    }

    private static class ObsoleteCollectionVisitor extends BaseInspectionVisitor {

        public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            final PsiType deepComponentType = type.getDeepComponentType();
            if (deepComponentType == null) {
                return;
            }
            if(!(deepComponentType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) deepComponentType;
            final String className = classType.getClassName();
            if(className == null || !className.startsWith("sun.")) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

        public void visitNewExpression(@NotNull PsiNewExpression newExpression) {
            super.visitNewExpression(newExpression);
            final PsiType type = newExpression.getType();
            if (type == null) {
                return;
            }
            if(!(type instanceof PsiClassType))
            {
                return;
            }
            final PsiClassType classType = (PsiClassType) type;
            final String className = classType.getClassName();
            if (className==null || !className.startsWith("sun.")) {
                return;
            }
            final PsiJavaCodeReferenceElement classNameElement = newExpression.getClassReference();
            registerError(classNameElement);
        }

    }

}
