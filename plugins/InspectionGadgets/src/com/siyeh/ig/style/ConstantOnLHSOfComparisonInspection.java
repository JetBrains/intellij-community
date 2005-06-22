package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;

public class ConstantOnLHSOfComparisonInspection extends ExpressionInspection {
    private final SwapComparisonFix fix = new SwapComparisonFix();

    public String getID(){
        return "ConstantOnLeftSideOfComparison";
    }

    public String getDisplayName() {
        return "Constant on left side of comparison";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref: constant on left side of comparison #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConstantOnLHSOfComparisonVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class SwapComparisonFix extends InspectionGadgetsFix {
        public String getName() {
            return "Flip comparison";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiBinaryExpression expression = (PsiBinaryExpression) descriptor.getPsiElement();
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression lhs = expression.getLOperand();
            final PsiJavaToken operation = expression.getOperationSign();
            final String sign = operation.getText();
            final String flippedSign = ComparisonUtils.getFlippedComparison(sign);
            assert rhs != null;
            final String rhsText = rhs.getText();
            final String lhsText = lhs.getText();
            replaceExpression(expression,
                    rhsText + ' ' + flippedSign + ' ' + lhsText);

        }

    }

    private static class ConstantOnLHSOfComparisonVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null))
            {
                return;
            }
            if (!ComparisonUtils.isComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            if (!PsiUtil.isConstantExpression(lhs)
                    || PsiUtil.isConstantExpression(rhs)) {
                return;
            }
            registerError(expression);
        }
    }

}
