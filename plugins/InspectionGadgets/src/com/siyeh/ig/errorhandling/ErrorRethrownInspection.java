package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TypeUtils;

public class ErrorRethrownInspection extends StatementInspection {

    public String getDisplayName() {
        return "Error not rethrown";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Error #ref not rethrown #loc";
    }

    protected BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ErrorRethrownVisitor(this, inspectionManager, onTheFly);
    }

    private static class ErrorRethrownVisitor extends BaseInspectionVisitor {
        private ErrorRethrownVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTryStatement(PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiParameter[] parameters = statement.getCatchBlockParameters();
            final PsiCodeBlock[] catchBlocks = statement.getCatchBlocks();
            for (int i = 0; i < catchBlocks.length; i++) {
                final PsiParameter parameter = parameters[i];
                final PsiCodeBlock catchBlock = catchBlocks[i];
                checkCatchBlock(parameter, catchBlock);
            }
        }

        private void checkCatchBlock(PsiParameter parameter, PsiCodeBlock catchBlock) {
            final PsiType type = parameter.getType();
            final PsiClass aClass = PsiUtil.resolveClassInType(type);
            if (!ClassUtils.isSubclass(aClass, "java.lang.Error")) {
                return;
            }
            if (TypeUtils.typeEquals("java.lang.ThreadDeath", type)) {
                return;
            }
            final PsiTypeElement typeElement = parameter.getTypeElement();
            if (typeElement == null) {
                return;
            }
            final PsiStatement[] statements = catchBlock.getStatements();
            if (statements.length == 0) {
                registerError(typeElement);
                return;
            }
            final PsiStatement lastStatement = statements[statements.length - 1];
            if (!(lastStatement instanceof PsiThrowStatement)) {
                registerError(typeElement);
                return;
            }
            final PsiThrowStatement throwStatement = (PsiThrowStatement) lastStatement;
            final PsiExpression exception = throwStatement.getException();
            if (!(exception instanceof PsiReferenceExpression)) {
                registerError(typeElement);
                return;
            }
            final PsiElement element = ((PsiReference) exception).resolve();
            if (!element.equals(parameter)) {
                registerError(typeElement);
                return;
            }
        }

    }
}
