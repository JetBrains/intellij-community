package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ComparisonUtils;

public class ConstantOnLHSOfComparisonInspection extends ExpressionInspection {
    private final SwapComparisonFix fix = new SwapComparisonFix();

    public String getDisplayName() {
        return "Constant on left side of comparison";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref: constant on left side of comparison #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ConstantOnLHSOfComparisonVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class SwapComparisonFix extends InspectionGadgetsFix {
        public String getName() {
            return "Flip comparison";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiBinaryExpression expression = (PsiBinaryExpression) descriptor.getPsiElement();
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression lhs = expression.getLOperand();
            final PsiJavaToken operation = expression.getOperationSign();
            final String sign = operation.getText();
            final String flippedSign = ComparisonUtils.getFlippedComparison(sign);
            final String rhsText = rhs.getText();
            final String lhsText = lhs.getText();
            replaceExpression(project, expression,
                    rhsText + ' ' + flippedSign + ' ' + lhsText);

        }

    }

    private static class ConstantOnLHSOfComparisonVisitor extends BaseInspectionVisitor {
        private ConstantOnLHSOfComparisonVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final String operator = sign.getText();
            if (!ComparisonUtils.isComparison(operator)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (lhs == null) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            if (!PsiUtil.isConstantExpression(lhs)
                    || PsiUtil.isConstantExpression(rhs)) {
                return;
            }
            registerError(expression);

        }
    }

}
