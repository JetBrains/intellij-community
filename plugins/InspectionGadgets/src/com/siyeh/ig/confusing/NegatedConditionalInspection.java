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

public class NegatedConditionalInspection extends ExpressionInspection{
    public boolean m_ignoreNegatedNullComparison = true;
    private final NegatedConditionalFix fix = new NegatedConditionalFix();

    public String getDisplayName(){
        return "Conditional expression with negated condition";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    protected BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                                  boolean onTheFly){
        return new NegatedConditionalVisitor(this, inspectionManager, onTheFly);
    }

    public String buildErrorString(PsiElement location){
        return "Conditional expression with negated condition #loc";
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore '!= null' comparisons",
                                              this,
                                              "m_ignoreNegatedNullComparison");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class NegatedConditionalFix extends InspectionGadgetsFix{
        public String getName(){
            return "Invert condition";
        }

        public void applyFix(Project project,
                             ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiConditionalExpression exp =
                    (PsiConditionalExpression) descriptor.getPsiElement()
                            .getParent();
            final PsiExpression elseBranch = exp.getElseExpression();
            final PsiExpression thenBranch = exp.getThenExpression();
            final PsiExpression condition = exp.getCondition();
            final String negatedCondition =
                    BoolUtils.getNegatedExpressionText(condition);
            final String newStatement =
            negatedCondition + '?' + elseBranch.getText() + ':' +
                    thenBranch.getText();
            replaceExpression(project, exp, newStatement);
        }
    }

    private class NegatedConditionalVisitor extends BaseInspectionVisitor{
        private NegatedConditionalVisitor(BaseInspection inspection,
                                          InspectionManager inspectionManager,
                                          boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitConditionalExpression(PsiConditionalExpression expression){
            super.visitConditionalExpression(expression);
            final PsiExpression thenBranch = expression.getThenExpression();
            if(thenBranch == null){
                return;
            }
            final PsiExpression elseBranch = expression.getElseExpression();
            if(elseBranch == null){
                return;
            }

            final PsiExpression condition = expression.getCondition();
            if(condition == null){
                return;
            }
            if(!isNegation(condition)){
                return;
            }
            registerError(condition);
        }

        private boolean isNegation(PsiExpression condition){
            if(condition instanceof PsiPrefixExpression){
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression) condition;
                final PsiJavaToken sign = prefixExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                return tokenType.equals(JavaTokenType.EXCL);
            } else if(condition instanceof PsiBinaryExpression){
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) condition;
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                if(lhs == null || rhs == null){
                    return false;
                }
                final IElementType tokenType = sign.getTokenType();
                if(tokenType.equals(JavaTokenType.NE)){
                    if(m_ignoreNegatedNullComparison){
                        final String lhsText = lhs.getText();
                        final String rhsText = rhs.getText();
                        return !"null".equals(lhsText) &&
                                !"null".equals(rhsText);
                    } else{
                        return true;
                    }
                } else{
                    return false;
                }
            } else if(condition instanceof PsiParenthesizedExpression){
                final PsiExpression expression =
                        ((PsiParenthesizedExpression) condition).getExpression();
                return isNegation(expression);
            } else{
                return false;
            }
        }
    }
}
