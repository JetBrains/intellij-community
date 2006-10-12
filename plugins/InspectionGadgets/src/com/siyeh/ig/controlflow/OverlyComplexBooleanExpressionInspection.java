/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ExtractMethodFix;
import com.siyeh.ig.ui.FormattedTextFieldMacFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;

public class OverlyComplexBooleanExpressionInspection
        extends ExpressionInspection {

    private static final Set<String> s_booleanOperators =
            new HashSet<String>(5);

    static {
        s_booleanOperators.add("&&");
        s_booleanOperators.add("||");
        s_booleanOperators.add("^");
        s_booleanOperators.add("&");
        s_booleanOperators.add("|");
    }

    private JFormattedTextField m_termLimitTextField;
    private JCheckBox m_ignoreConjunctionsDisjunctionsCheckBox;
    private JPanel m_contentPanel;

    /** @noinspection PublicField*/
    public int m_limit = 3;

    /** @noinspection PublicField*/
    public boolean m_ignorePureConjunctionsDisjunctions = true;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "overly.complex.boolean.expression.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "overly.complex.boolean.expression.problem.descriptor");
    }

    public JComponent createOptionsPanel() {
        final ButtonModel pureModel =
                m_ignoreConjunctionsDisjunctionsCheckBox.getModel();
        pureModel.setSelected(m_ignorePureConjunctionsDisjunctions);
        pureModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignorePureConjunctionsDisjunctions = pureModel.isSelected();
            }
        });
        final NumberFormat formatter = NumberFormat.getIntegerInstance();
        formatter.setParseIntegerOnly(true);
        m_termLimitTextField.setValue(m_limit);
        m_termLimitTextField.setColumns(4);
        FormattedTextFieldMacFix.apply(m_termLimitTextField);
        final Document document = m_termLimitTextField.getDocument();
        document.addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                textChanged();
            }

            public void insertUpdate(DocumentEvent e) {
                textChanged();
            }

            public void removeUpdate(DocumentEvent e) {
                textChanged();
            }

            private void textChanged() {
                try {
                    m_termLimitTextField.commitEdit();
                    final Number number = (Number)m_termLimitTextField.getValue();
                    m_limit = number.intValue();
                } catch (ParseException e) {
                    // No luck this time
                }
            }
        });
        return m_contentPanel;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new ExtractMethodFix();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SwitchStatementWithTooManyBranchesVisitor();
    }

    private class SwitchStatementWithTooManyBranchesVisitor
            extends BaseInspectionVisitor {

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            checkExpression(expression);
        }

        public void visitPrefixExpression(
                @NotNull PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            checkExpression(expression);
        }

        public void visitParenthesizedExpression(
                @NotNull PsiParenthesizedExpression expression) {
            super.visitParenthesizedExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(PsiExpression expression) {
            if (!isBoolean(expression)) {
                return;
            }
            if (isParentBoolean(expression)) {
                return;
            }
            final int numTerms = countTerms(expression);
            if (numTerms <= m_limit) {
                return;
            }
            if (m_ignorePureConjunctionsDisjunctions &&
                expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)expression;
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final String signText = sign.getText();
                if (s_booleanOperators.contains(signText)) {
                    if (isPureConjunctionDisJunction(expression, signText)) {
                        return;
                    }
                }
            }
            registerError(expression);
        }

        private int countTerms(PsiExpression expression) {
            if (!isBoolean(expression)) {
                return 1;
            }
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) expression;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                return countTerms(lhs) + countTerms(rhs);
            } else if (expression instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression) expression;
                final PsiExpression operand = prefixExpression.getOperand();
                return countTerms(operand);
            } else if (expression instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression =
                        (PsiParenthesizedExpression) expression;
                final PsiExpression contents =
                        parenthesizedExpression.getExpression();
                return countTerms(contents);
            }
            return 1;
        }

        private boolean isParentBoolean(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiExpression)) {
                return false;
            }
            return isBoolean((PsiExpression) parent);
        }

        private boolean isBoolean(PsiExpression expression) {
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) expression;
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final String signText = sign.getText();
                return s_booleanOperators.contains(signText);
            } else if (expression instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression) expression;
                final PsiJavaToken sign = prefixExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                return tokenType.equals(JavaTokenType.EXCL);
            } else if (expression instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression =
                        (PsiParenthesizedExpression) expression;
                final PsiExpression contents =
                        parenthesizedExpression.getExpression();
                return isBoolean(contents);
            }
            return false;
        }

        private boolean isPureConjunctionDisJunction(
                @Nullable PsiExpression expression,
                @NotNull String operator) {
            if (expression == null) {
                return false;
            }
            if (expression instanceof PsiPrefixExpression ||
                expression instanceof PsiParenthesizedExpression) {
                return false;
            }
            if (!(expression instanceof PsiBinaryExpression)) {
                return true;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final String signText = sign.getText();
            if (s_booleanOperators.contains(signText) &&
                !operator.equals(signText)) {
                return false;
            }
            final PsiExpression lOperand = binaryExpression.getLOperand();
            final PsiExpression rOperand = binaryExpression.getROperand();
            return isPureConjunctionDisJunction(lOperand, operator) &&
                   isPureConjunctionDisJunction(rOperand, operator);
        }
    }
}