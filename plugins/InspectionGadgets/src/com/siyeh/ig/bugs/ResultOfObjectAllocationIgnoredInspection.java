package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class ResultOfObjectAllocationIgnoredInspection extends ExpressionInspection {


    public String getDisplayName() {
        return "Result of object allocation ignored";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "result of new #ref() is ignored. #loc ";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new IgnoreResultOfCallVisitor();
    }

    private static class IgnoreResultOfCallVisitor extends BaseInspectionVisitor {

        public void visitExpressionStatement(@NotNull PsiExpressionStatement statement) {
            super.visitExpressionStatement(statement);
            if (!(statement.getExpression() instanceof PsiNewExpression)) {
                return;
            }
            final PsiNewExpression newExpression = (PsiNewExpression) statement.getExpression();
            final PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
            if (arrayDimensions.length != 0) {
                return;
            }
            if (newExpression.getArrayInitializer() != null) {
                return;
            }
            final PsiJavaCodeReferenceElement classReference =
                    newExpression.getClassReference();
            registerError(classReference);

        }

    }
}
