package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;

public class ReturnOfDateFieldInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Return of Date or Calendar field";
    }

    public String getGroupDisplayName() {
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiField field = (PsiField) ((PsiReference) location).resolve();
        final PsiType type = field.getType();
        return "'return' of " + type.getPresentableText() + " field #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ReturnOfDateFieldVisitor(this, inspectionManager, onTheFly);
    }

    private static class ReturnOfDateFieldVisitor extends BaseInspectionVisitor {
        private ReturnOfDateFieldVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReturnStatement(PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
            final PsiExpression returnValue = statement.getReturnValue();
            if (returnValue == null) {
                return;
            }
            if (!(returnValue instanceof PsiReferenceExpression)) {
                return ;
            }
            final PsiReferenceExpression fieldReference = (PsiReferenceExpression) returnValue;

            final PsiElement element = fieldReference.resolve();
            if (!(element instanceof PsiField)) {
                return;
            }
            if (!TypeUtils.expressionHasTypeOrSubtype("java.util.Date", returnValue)
                    && !TypeUtils.expressionHasTypeOrSubtype("java.util.Calendar", returnValue)) {
                return;
            }
            registerError(returnValue);
        }

    }

}
