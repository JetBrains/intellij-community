package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.BoolUtils;

public class UnnecessaryConditionalExpressionInspection
        extends ExpressionInspection {
    private final TrivialConditionalFix fix = new TrivialConditionalFix();

    public String getID(){
        return "RedundantConditionalExpression";
    }

    public String getDisplayName() {
        return "Redundant conditional expression";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryConditionalExpressionVisitor(this, inspectionManager,
                onTheFly);
    }

    public String buildErrorString(PsiElement location) {
        final PsiConditionalExpression exp = (PsiConditionalExpression) location;
        return '\'' + exp.getText() + "' can be simplified to '" +
                calculateReplacementExpression(exp) +
                "' #loc";
    }

    private static String calculateReplacementExpression(PsiConditionalExpression exp) {
        final PsiExpression thenExpression = exp.getThenExpression();
        final PsiExpression elseExpression = exp.getElseExpression();
        final PsiExpression condition = exp.getCondition();

        if (isFalse(thenExpression) && isTrue(elseExpression)) {
            return BoolUtils.getNegatedExpressionText(condition);
        } else {
            return condition.getText();
        }
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class TrivialConditionalFix extends InspectionGadgetsFix {
        public String getName() {
            return "Simplify";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiConditionalExpression expression = (PsiConditionalExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression(project, expression, newExpression);
        }
    }

    private static class UnnecessaryConditionalExpressionVisitor
            extends BaseInspectionVisitor {
        private UnnecessaryConditionalExpressionVisitor(BaseInspection inspection,
                                                        InspectionManager inspectionManager,
                                                        boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitConditionalExpression(PsiConditionalExpression exp) {
            super.visitConditionalExpression(exp);
            final PsiExpression thenExpression = exp.getThenExpression();
            if (thenExpression == null) {
                return;
            }
            final PsiExpression elseExpression = exp.getElseExpression();
            if (elseExpression == null) {
                return;
            }
            if ((isFalse(thenExpression) && isTrue(elseExpression))
                    || (isTrue(thenExpression) && isFalse(elseExpression))) {
                registerError(exp);
            }
        }
    }

    private static boolean isFalse(PsiExpression expression) {
        final String text = expression.getText();
        return "false".equals(text);
    }

    private static boolean isTrue(PsiExpression expression) {
        final String text = expression.getText();
        return "true".equals(text);
    }


}
