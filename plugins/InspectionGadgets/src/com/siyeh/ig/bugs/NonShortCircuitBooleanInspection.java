package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class NonShortCircuitBooleanInspection extends ExpressionInspection {
    public String getID(){
        return "NonShortCircuitBooleanExpression";
    }
    private final InspectionGadgetsFix fix = new NonShortCircuitBooleanFix();

    public String getDisplayName() {
        return "Non-short-circuit boolean expression";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-short-circuit boolean expression #ref #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class NonShortCircuitBooleanFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with short circuit expression";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiBinaryExpression expression = (PsiBinaryExpression) descriptor.getPsiElement();
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            final PsiJavaToken operationSign = expression.getOperationSign();
            final IElementType tokenType = operationSign.getTokenType();
            assert rhs != null;
            final String newExpression = lhs.getText() + getShortCircuitOperand(tokenType) + rhs.getText();
            replaceExpression(expression, newExpression);
        }

        private static String getShortCircuitOperand(IElementType tokenType) {
            if (tokenType.equals(JavaTokenType.AND)) {
                return "&&";
            }
            else {
                return "||";
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonShortCircuitBooleanVisitor();
    }

    private static class NonShortCircuitBooleanVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }

            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.AND) &&
                    !tokenType.equals(JavaTokenType.OR)) {
                return;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (!type.equals(PsiType.BOOLEAN)) {
                return;
            }
            registerError(expression);
        }
    }
}
