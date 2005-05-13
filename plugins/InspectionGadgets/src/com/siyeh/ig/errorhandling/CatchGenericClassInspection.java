package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ExceptionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class CatchGenericClassInspection extends StatementInspection {

    public String getDisplayName() {
        return "'catch' generic class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "catch of generic #ref class should be replaced by more precise exception #loc";
    }

    protected BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CatchGenericClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class CatchGenericClassVisitor extends StatementInspectionVisitor {
        private CatchGenericClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCodeBlock tryBlock = statement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final Set<PsiType> exceptionsThrown =
                    ExceptionUtils.calculateExceptionsThrown(tryBlock);
            final PsiParameter[] parameters = statement.getCatchBlockParameters();
            for(final PsiParameter parameter : parameters){
                checkParameter(parameter, exceptionsThrown);
            }
        }

        private void checkParameter(PsiParameter parameter, Set<PsiType> exceptionsThrown) {
            final PsiType type = parameter.getType();
            if (type == null) {
                return;
            }
            if (!ExceptionUtils.isGenericExceptionClass(type)) {
                return;
            }
            if (exceptionsThrown.contains(type)) {
                return;
            }
            final PsiTypeElement typeElement = parameter.getTypeElement();
            registerError(typeElement);
        }
    }
}
