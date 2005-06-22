package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NegatedIfElseInspection extends StatementInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreNegatedNullComparison = true;
    private final NegatedIfElseFix fix = new NegatedIfElseFix();

    public String getID(){
        return "IfStatementWithNegatedCondition";
    }

    public String getDisplayName() {
        return "'if' statement with negated condition";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NegatedIfElseVisitor();
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

        public void doFix(Project project,
                             ProblemDescriptor descriptor)
                                                           throws IncorrectOperationException{
            final PsiElement ifToken = descriptor.getPsiElement();
            final PsiIfStatement ifStatement = (PsiIfStatement) ifToken.getParent();
            assert ifStatement != null;
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiExpression condition = ifStatement.getCondition();
            final String negatedCondition = BoolUtils.getNegatedExpressionText(condition);
            final String newStatement = "if("+ negatedCondition + ')' +elseBranch.getText() + " else " + thenBranch.getText();
            replaceStatement(ifStatement, newStatement);
        }
    }
    private class NegatedIfElseVisitor extends StatementInspectionVisitor {

        public void visitIfStatement(@NotNull PsiIfStatement statement) {
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
                if (rhs == null) {
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
