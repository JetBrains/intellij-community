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
package com.siyeh.ipp.switchtoif;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ControlFlowUtils;
import com.siyeh.ipp.psiutils.DeclarationUtils;
import com.siyeh.ipp.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReplaceIfWithSwitchIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new IfToSwitchPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiJavaToken switchToken =
                (PsiJavaToken)element;
        PsiIfStatement ifStatement = (PsiIfStatement)switchToken.getParent();
        assert ifStatement != null;
        boolean breaksNeedRelabeled = false;
        PsiStatement breakTarget = null;
        String labelString = "";
        if (ControlFlowUtils.statementContainsExitingBreak(ifStatement)) {
            // what a pain.
            PsiElement ancestor = ifStatement.getParent();
            while (ancestor != null) {
                if (ancestor instanceof PsiForStatement ||
                        ancestor instanceof PsiDoWhileStatement ||
                        ancestor instanceof PsiWhileStatement ||
                        ancestor instanceof PsiSwitchStatement) {
                    breakTarget = (PsiStatement)ancestor;
                    break;
                }
                ancestor = ancestor.getParent();
            }
            if (breakTarget != null) {
                labelString = CaseUtil.findUniqueLabel(ifStatement, "Label");
                breaksNeedRelabeled = true;
            }
        }
        final PsiIfStatement statementToReplace = ifStatement;
        final PsiExpression caseExpression =
                CaseUtil.getCaseExpression(ifStatement);
        assert caseExpression != null;

        final List<IfStatementBranch> branches =
                new ArrayList<IfStatementBranch>(20);
        while (true) {
            final Set<String> topLevelVariables = new HashSet<String>(5);
            final Set<String> innerVariables = new HashSet<String>(5);
            final PsiExpression condition = ifStatement.getCondition();
            final PsiExpression[] labels =
                    getValuesFromCondition(condition, caseExpression);
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            DeclarationUtils.calculateVariablesDeclared(thenBranch,
                    topLevelVariables,
                    innerVariables,
                    true);
            final IfStatementBranch ifBranch = new IfStatementBranch();
            ifBranch.setInnerVariables(innerVariables);
            ifBranch.setTopLevelVariables(topLevelVariables);
            ifBranch.setStatement(thenBranch);
            for (final PsiExpression label : labels) {
                if (label instanceof PsiReferenceExpression) {
                    final PsiReferenceExpression reference =
                            (PsiReferenceExpression)label;
                    final PsiElement referent = reference.resolve();
                    if (referent instanceof PsiEnumConstant) {
                        final PsiEnumConstant constant = (PsiEnumConstant)referent;
                        final String constantName = constant.getName();
                        ifBranch.addCondition(constantName);
                    } else {
                        final String labelText = label.getText();
                        ifBranch.addCondition(labelText);
                    }
                } else {
                    final String labelText = label.getText();
                    ifBranch.addCondition(labelText);
                }
            }
            branches.add(ifBranch);
            final PsiStatement elseBranch = ifStatement.getElseBranch();

            if (elseBranch instanceof PsiIfStatement) {
                ifStatement = (PsiIfStatement)elseBranch;
            } else if (elseBranch == null) {
                break;
            } else {
                final Set<String> elseTopLevelVariables = new HashSet<String>(5);
                final Set<String> elseInnerVariables = new HashSet<String>(5);
                DeclarationUtils.calculateVariablesDeclared(
                        elseBranch, elseTopLevelVariables, elseInnerVariables,
                        true);
                final IfStatementBranch elseIfBranch = new IfStatementBranch();
                elseIfBranch.setInnerVariables(elseInnerVariables);
                elseIfBranch.setTopLevelVariables(elseTopLevelVariables);
                elseIfBranch.setElse();
                elseIfBranch.setStatement(elseBranch);
                branches.add(elseIfBranch);
                break;
            }
        }

        @NonNls final StringBuilder switchStatementBuffer =
                new StringBuilder(1024);
        switchStatementBuffer.append("switch(");
        switchStatementBuffer.append(caseExpression.getText());
        switchStatementBuffer.append(')');
        switchStatementBuffer.append('{');
        for (IfStatementBranch branch : branches) {
            boolean hasConflicts = false;
            for (Object branche1 : branches) {
                final IfStatementBranch testBranch =
                        (IfStatementBranch)branche1;
                if (branch.topLevelDeclarationsConfictWith(testBranch)) {
                    hasConflicts = true;
                }
            }

            final PsiStatement branchStatement = branch.getStatement();
            if (branch.isElse()) {
                dumpDefaultBranch(switchStatementBuffer, branchStatement,
                        hasConflicts,
                        breaksNeedRelabeled, labelString);
            } else {
                final List<String> conditions = branch.getConditions();
                dumpBranch(switchStatementBuffer, conditions, branchStatement,
                        hasConflicts, breaksNeedRelabeled, labelString);
            }
        }
        switchStatementBuffer.append('}');
        final String switchStatementString = switchStatementBuffer.toString();
        if (breaksNeedRelabeled) {
            final int length = switchStatementBuffer.length();
            final StringBuilder out = new StringBuilder(length);
            out.append(labelString);
            out.append(':');
            termReplace(out, breakTarget, statementToReplace,
                    switchStatementString);
            final String newStatement = out.toString();
            replaceStatement(newStatement, breakTarget);
        } else {
            replaceStatement(switchStatementString,
                    statementToReplace);
        }
    }

    private static void termReplace(
            StringBuilder out, PsiElement target,
            PsiElement replace, String stringToReplaceWith) {
        if (target.equals(replace)) {
            out.append(stringToReplaceWith);
        } else if (target.getChildren().length == 0) {
            final String text = target.getText();
            out.append(text);
        } else {
            final PsiElement[] children = target.getChildren();
            for (final PsiElement child : children) {
                termReplace(out, child, replace, stringToReplaceWith);
            }
        }
    }

    private static PsiExpression[] getValuesFromCondition(
            PsiExpression condition, PsiExpression caseExpression) {
        final List<PsiExpression> values = new ArrayList<PsiExpression>(10);
        getValuesFromExpression(condition, caseExpression, values);
        return values.toArray(new PsiExpression[values.size()]);
    }

    private static void getValuesFromExpression(PsiExpression expression,
                                                PsiExpression caseExpression,
                                                List<PsiExpression> values) {
        if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (JavaTokenType.OROR.equals(tokenType)) {
                getValuesFromExpression(lhs, caseExpression,
                        values);
                getValuesFromExpression(rhs, caseExpression,
                        values);
            } else {
                if (EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                        rhs)) {
                    values.add(lhs);
                } else {
                    values.add(rhs);
                }
            }
        } else if (expression instanceof PsiParenthesizedExpression) {
            final PsiParenthesizedExpression parenExpression =
                    (PsiParenthesizedExpression)expression;
            final PsiExpression contents = parenExpression.getExpression();
            getValuesFromExpression(contents, caseExpression, values);
        }
    }

    private static void dumpBranch(StringBuilder switchStatementString,
                                   List<String> labels, PsiStatement body,
                                   boolean wrap, boolean renameBreaks,
                                   String breakLabelName) {
        dumpLabels(switchStatementString, labels);
        dumpBody(switchStatementString, body, wrap, renameBreaks,
                breakLabelName);
    }

    private static void dumpDefaultBranch(
            @NonNls StringBuilder switchStatementString,
            PsiStatement body, boolean wrap,
            boolean renameBreaks, String breakLabelName) {
        switchStatementString.append("default: ");
        dumpBody(switchStatementString, body, wrap, renameBreaks,
                breakLabelName);
    }

    private static void dumpLabels(@NonNls StringBuilder switchStatementString,
                                   List<String> labels) {
        for (String label : labels) {
            switchStatementString.append("case ");
            switchStatementString.append(label);
            switchStatementString.append(": ");
        }
    }

    private static void dumpBody(@NonNls StringBuilder switchStatementString,
                                 PsiStatement bodyStatement, boolean wrap,
                                 boolean renameBreaks, String breakLabelName) {
        if (bodyStatement instanceof PsiBlockStatement) {
            if (wrap) {
                appendElement(switchStatementString, bodyStatement,
                        renameBreaks, breakLabelName);
            } else {
                final PsiCodeBlock codeBlock =
                        ((PsiBlockStatement)bodyStatement).getCodeBlock();
                final PsiElement[] children = codeBlock.getChildren();
                //skip the first and last members, to unwrap the block
                for (int i = 1; i < children.length - 1; i++) {
                    final PsiElement child = children[i];
                    appendElement(switchStatementString, child, renameBreaks,
                            breakLabelName);
                }
            }
        } else {
            if (wrap) {
                switchStatementString.append('{');
                appendElement(switchStatementString, bodyStatement,
                        renameBreaks, breakLabelName);
                switchStatementString.append('}');
            } else {
                appendElement(switchStatementString, bodyStatement,
                        renameBreaks, breakLabelName);
            }
        }
        if (ControlFlowUtils.statementMayCompleteNormally(bodyStatement)) {
            switchStatementString.append("break; ");
        }
    }

    private static void appendElement(
            @NonNls StringBuilder switchStatementString,
            PsiElement element, boolean renameBreakElements,
            String breakLabelString) {
        final String text = element.getText();
        if (!renameBreakElements) {
            switchStatementString.append(text);
        } else if (element instanceof PsiBreakStatement) {
            final PsiIdentifier identifier =
                    ((PsiBreakStatement)element).getLabelIdentifier();
            if (identifier == null) {
                switchStatementString.append("break ");
                switchStatementString.append(breakLabelString);
                switchStatementString.append(';');
            } else {
                final String identifierText = identifier.getText();
                if ("".equals(identifierText)) {
                    switchStatementString.append("break ");
                    switchStatementString.append(breakLabelString);
                    switchStatementString.append(';');
                } else {
                    switchStatementString.append(text);
                }
            }
        } else if (element instanceof PsiBlockStatement ||
                element instanceof PsiCodeBlock ||
                element instanceof PsiIfStatement) {
            final PsiElement[] children = element.getChildren();
            for (final PsiElement child : children) {
                appendElement(switchStatementString, child, renameBreakElements,
                        breakLabelString);
            }
        } else {
            switchStatementString.append(text);
        }
    }
}
