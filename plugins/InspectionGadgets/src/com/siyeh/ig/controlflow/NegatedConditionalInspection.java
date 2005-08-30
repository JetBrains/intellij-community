/*
 * Copyright 2003-2005 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class NegatedConditionalInspection extends ExpressionInspection{
    /** @noinspection PublicField*/
    public boolean m_ignoreNegatedNullComparison = true;
    private final NegatedConditionalFix fix = new NegatedConditionalFix();

    public String getID(){
        return "ConditionalExpressionWithNegatedCondition";
    }

    public String getDisplayName(){
        return "Conditional expression with negated condition";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NegatedConditionalVisitor();
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

        public void doFix(Project project,
                             ProblemDescriptor descriptor)
                                                           throws IncorrectOperationException{
            final PsiConditionalExpression exp =
                    (PsiConditionalExpression) descriptor.getPsiElement()
                            .getParent();
            assert exp != null;
            final PsiExpression elseBranch = exp.getElseExpression();
            final PsiExpression thenBranch = exp.getThenExpression();
            final PsiExpression condition = exp.getCondition();
            final String negatedCondition =
                    BoolUtils.getNegatedExpressionText(condition);
            assert elseBranch != null;
            assert thenBranch != null;
            final String newStatement =
            negatedCondition + '?' + elseBranch.getText() + ':' +
                    thenBranch.getText();
            replaceExpression(exp, newStatement);
        }
    }

    private class NegatedConditionalVisitor extends BaseInspectionVisitor{

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
                if(rhs == null){
                    return false;
                }
                final IElementType tokenType = sign.getTokenType();
                if(tokenType.equals(JavaTokenType.NE)){
                    if(m_ignoreNegatedNullComparison){
                        final String lhsText = lhs.getText();
                        final String rhsText = rhs.getText();
                        return !PsiKeyword.NULL.equals(lhsText) &&
                               !PsiKeyword.NULL.equals(rhsText);
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
