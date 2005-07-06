package com.siyeh.ig.portability;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import org.jetbrains.annotations.NotNull;

public class UseOfProcessBuilderInspection extends VariableInspection {

    public String getDisplayName() {
        return "Use of java.lang.ProcessBuilder class";
    }

    public String getGroupDisplayName() {
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Use of #ref is non-portable #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ProcessBuilderVisitor();
    }

    private static class ProcessBuilderVisitor extends BaseInspectionVisitor {
        private static final String PROCESS_BUILDER_CLASS_NAME = "java.lang.ProcessBuilder";

        public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            final String typeString = type.getCanonicalText();
            if(!PROCESS_BUILDER_CLASS_NAME.equals(typeString))
            {
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
            final String typeString = type.getCanonicalText();
            if(!PROCESS_BUILDER_CLASS_NAME.equals(typeString))
            {
               return;
            }
            final PsiJavaCodeReferenceElement classNameElement = newExpression.getClassReference();
            registerError(classNameElement);
        }

    }

}
