package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class NegatedIfElseInspection extends StatementInspection {
    public boolean m_ignoreNegatedNullComparison = true;
    private final NegatedIfElseFix fix = new NegatedIfElseFix();

    public String getDisplayName() {
        return "If statement with negated condition";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    protected BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NegatedIfElseVisitor(this, inspectionManager, onTheFly);
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement with negated condition #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore '!= null' comparisons",
                this, "m_ignoreNegatedNullComparison");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class NegatedIfElseFix extends InspectionGadgetsFix{


        public String getName(){
            return "Invert If Condition";
        }

        public void applyFix(Project project,
                             ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement ifToken = descriptor.getPsiElement();
            final PsiIfStatement ifStatement = (PsiIfStatement) ifToken.getParent();
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiExpression condition = ifStatement.getCondition();
            final String negatedCondition = BoolUtils.getNegatedExpressionText(condition);
            final String newStatement = "if("+ negatedCondition + ')' +elseBranch.getText() + " else " + thenBranch.getText();
            replaceStatement(project, ifStatement, newStatement);
        }
    }
    private class NegatedIfElseVisitor extends BaseInspectionVisitor {
        private NegatedIfElseVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            if (elseBranch instanceof PsiIfStatement) {
                return;
            }

            final PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            if (!isNegation(condition)) {
                return;
            }
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean isNegation(PsiExpression condition) {
            if (condition instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) condition;
                final PsiJavaToken sign = prefixExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                return tokenType.equals(JavaTokenType.EXCL);
            } else if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) condition;
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                if (lhs == null || rhs == null) {
                    return false;
                }
                final IElementType tokenType = sign.getTokenType();
                if (tokenType.equals(JavaTokenType.NE)) {
                    if (m_ignoreNegatedNullComparison) {
                        final String lhsText = lhs.getText();
                        final String rhsText = rhs.getText();
                        return !"null".equals(lhsText) && !"null".equals(rhsText);
                    } else {
                        return true;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

    }

}
