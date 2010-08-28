/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ControlFlowUtils;
import com.siyeh.ipp.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReplaceSwitchWithIfIntention extends Intention {

    @Override
    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new SwitchPredicate();
    }

    @Override
    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiJavaToken switchToken = (PsiJavaToken)element;
        final PsiSwitchStatement switchStatement =
                (PsiSwitchStatement)switchToken.getParent();
        if (switchStatement == null) {
            return;
        }
        final PsiExpression switchExpression = switchStatement.getExpression();
        if (switchExpression == null) {
            return;
        }
        final String declarationString;
        final boolean hadSideEffects;
        final String expressionText;
        final Project project = element.getProject();
        if (SideEffectChecker.mayHaveSideEffects(switchExpression)) {
            hadSideEffects = true;
            final PsiType switchExpressionType = switchExpression.getType();
            if (switchExpressionType == null) {
                return;
            }
            final JavaCodeStyleManager javaCodeStyleManager =
                    JavaCodeStyleManager.getInstance(project);
            final String variableName =
                    javaCodeStyleManager.suggestUniqueVariableName("i",
                            switchExpression, true);
            expressionText = variableName;
            declarationString =
                    switchExpressionType.getPresentableText() + ' ' +
                            variableName + " = " +
                            switchExpression.getText() + ';';
        } else {
            hadSideEffects = false;
            declarationString = null;
            expressionText = switchExpression.getText();
        }
        final PsiCodeBlock body = switchStatement.getBody();
        if (body == null) {
            return;
        }
        final PsiStatement[] statements = body.getStatements();
        boolean renameBreaks = false;
        for (int i = 1; i < statements.length - 1; i++) {
            if (CaseUtil.containsHiddenBreak(statements[i])) {
                renameBreaks = true;
                break;
            }
        }

        final List<SwitchStatementBranch> openBranches =
                new ArrayList<SwitchStatementBranch>();
        final Set<PsiLocalVariable> declaredVariables =
                new HashSet<PsiLocalVariable>();
        final List<SwitchStatementBranch> allBranches =
                new ArrayList<SwitchStatementBranch>();
        SwitchStatementBranch currentBranch = null;
        final PsiElement[] children = body.getChildren();
        for (int i = 1; i < children.length - 1; i++) {
            final PsiElement statement = children[i];
            if (statement instanceof PsiSwitchLabelStatement) {
                final PsiSwitchLabelStatement label =
                        (PsiSwitchLabelStatement)statement;
                if (currentBranch == null) {
                    openBranches.clear();
                    currentBranch = new SwitchStatementBranch();
                    currentBranch.addPendingVariableDeclarations(declaredVariables);
                    allBranches.add(currentBranch);
                    openBranches.add(currentBranch);
                } else if (currentBranch.hasStatements()) {
                    currentBranch = new SwitchStatementBranch();
                    allBranches.add(currentBranch);
                    openBranches.add(currentBranch);
                }
                if (label.isDefaultCase()) {
                    currentBranch.setDefault();
                } else {
                    final PsiExpression value = label.getCaseValue();
                    final String valueText = getCaseValueText(value);
                    currentBranch.addCaseValue(valueText);
                }
            } else {
                if (statement instanceof PsiStatement) {
                    if (statement instanceof PsiDeclarationStatement) {
                        final PsiDeclarationStatement declarationStatement =
                                (PsiDeclarationStatement)statement;
                        final PsiElement[] elements =
                                declarationStatement.getDeclaredElements();
                        for (PsiElement varElement : elements) {
                            final PsiLocalVariable variable =
                                    (PsiLocalVariable)varElement;
                            declaredVariables.add(variable);
                        }
                    }
                    for (SwitchStatementBranch branch : openBranches) {
                        branch.addStatement(statement);
                    }
                    if (!ControlFlowUtils.statementMayCompleteNormally(
                            (PsiStatement)statement)) {
                        currentBranch = null;
                    }
                } else {
                    for (SwitchStatementBranch branch : openBranches) {
                        if (statement instanceof PsiWhiteSpace) {
                            branch.addWhiteSpace(statement);
                        } else {
                            branch.addComment(statement);
                        }
                    }
                }
            }
        }
        final StringBuilder ifStatementText = new StringBuilder();
        String breakLabel = null;
        if (renameBreaks) {
            breakLabel = CaseUtil.findUniqueLabel(switchStatement, "Label");
            ifStatementText.append(breakLabel);
            ifStatementText.append(':');
        }
        boolean firstBranch = true;
        SwitchStatementBranch defaultBranch = null;
        for (SwitchStatementBranch branch : allBranches) {
            if (branch.isDefault()) {
                defaultBranch = branch;
            } else {
                final List<String> caseValues = branch.getCaseValues();
                final List<PsiElement> bodyElements = branch.getBodyElements();
                final Set<PsiLocalVariable> pendingVariableDeclarations =
                        branch.getPendingVariableDeclarations();
                dumpBranch(ifStatementText, expressionText,
                        caseValues, bodyElements, firstBranch,
                        renameBreaks, breakLabel,
                        pendingVariableDeclarations);
                firstBranch = false;
            }
        }
        if (defaultBranch != null) {
            final List<PsiElement> bodyElements =
                    defaultBranch.getBodyElements();
            final Set<PsiLocalVariable> pendingVariableDeclarations =
                    defaultBranch.getPendingVariableDeclarations();
            dumpDefaultBranch(ifStatementText, bodyElements,
                    firstBranch, renameBreaks, breakLabel,
                    pendingVariableDeclarations);
        }
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory factory = psiFacade.getElementFactory();
        if (hadSideEffects) {
            final PsiStatement declarationStatement =
                    factory.createStatementFromText(declarationString,
                            switchStatement);
            final PsiStatement ifStatement =
                    factory.createStatementFromText(ifStatementText.toString(),
                            switchStatement);
            final PsiElement parent = switchStatement.getParent();
            parent.addBefore(declarationStatement, switchStatement);
            switchStatement.replace(ifStatement);
        } else {
            final PsiStatement newStatement =
                    factory.createStatementFromText(ifStatementText.toString(),
                            switchStatement);
            switchStatement.replace(newStatement);
        }
    }

    private static String getCaseValueText(PsiExpression value) {
        if (value == null) {
            return "";
        }
        if (value instanceof PsiParenthesizedExpression) {
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression) value;
            final PsiExpression expression =
                    parenthesizedExpression.getExpression();
            return getCaseValueText(expression);
        }
        if (!(value instanceof PsiReferenceExpression)) {
            return value.getText();
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) value;
        final PsiElement target = referenceExpression.resolve();
        final String text = referenceExpression.getText();
        if (!(target instanceof PsiEnumConstant)) {
            return value.getText();
        }
        final PsiEnumConstant enumConstant = (PsiEnumConstant) target;
        final PsiClass aClass = enumConstant.getContainingClass();
        if (aClass == null) {
            return value.getText();
        }
        final String name = aClass.getQualifiedName();
        return name + '.' + text;
    }

    private static void dumpBranch(@NonNls StringBuilder ifStatementString,
                                   String expressionText, List<String> caseValues,
                                   List<PsiElement> bodyStatements,
                                   boolean firstBranch,
                                   boolean renameBreaks,
                                   String breakLabel,
                                   Set<PsiLocalVariable> variables) {
        if (!firstBranch) {
            ifStatementString.append("else ");
        }
        dumpCaseValues(ifStatementString, expressionText, caseValues);
        dumpBody(ifStatementString, bodyStatements, renameBreaks, breakLabel,
                variables);
    }

    private static void dumpDefaultBranch(@NonNls StringBuilder ifStatementString,
                                          List<PsiElement> bodyStatements,
                                          boolean firstBranch,
                                          boolean renameBreaks,
                                          String breakLabel,
                                          Set<PsiLocalVariable> variables) {
        if (!firstBranch) {
            ifStatementString.append("else ");
        }
        dumpBody(ifStatementString, bodyStatements, renameBreaks, breakLabel,
                variables);
    }

    private static void dumpCaseValues(@NonNls StringBuilder ifStatementString,
                                   String expressionText, List<String> caseValues) {
        ifStatementString.append("if(");
        boolean firstCaseValue = true;
        for (String caseValue : caseValues) {
            if (!firstCaseValue) {
                ifStatementString.append("||");
            }
            firstCaseValue = false;
            ifStatementString.append(expressionText);
            ifStatementString.append("==");
            ifStatementString.append(caseValue);
        }
        ifStatementString.append(')');
    }

    private static void dumpBody(@NonNls StringBuilder ifStatementString,
                                 List<PsiElement> bodyStatements,
                                 boolean renameBreaks,
                                 String breakLabel,
                                 Set<PsiLocalVariable> variables) {
        ifStatementString.append('{');
        for (PsiLocalVariable variable : variables) {
            if (CaseUtil.isUsedByStatementList(variable, bodyStatements)) {
                final PsiType varType = variable.getType();
                ifStatementString.append(varType.getPresentableText());
                ifStatementString.append(' ');
                ifStatementString.append(variable.getName());
                ifStatementString.append(';');
            }
        }
        for (PsiElement bodyStatement : bodyStatements) {
            @NonNls final String text = bodyStatement.getText();
            if (!"break;".equals(text)) {
                appendElement(ifStatementString, bodyStatement, renameBreaks,
                        breakLabel);
            }
        }
        ifStatementString.append("\n}");
    }

    private static void appendElement(@NonNls StringBuilder ifStatementString,
                                      PsiElement element,
                                      boolean renameBreakElements,
                                      String breakLabelString) {
        if (!renameBreakElements) {
            final String text = element.getText();
            ifStatementString.append(text);
        } else if (element instanceof PsiBreakStatement) {
            final PsiIdentifier identifier =
                    ((PsiBreakStatement)element).getLabelIdentifier();
            if (identifier == null || "".equals(identifier.getText())) {
                ifStatementString.append("break ");
                ifStatementString.append(breakLabelString);
                ifStatementString.append(';');
            } else {
                final String text = element.getText();
                ifStatementString.append(text);
            }
        } else if (element instanceof PsiBlockStatement ||
                element instanceof PsiCodeBlock ||
                element instanceof PsiIfStatement) {
            final PsiElement[] children = element.getChildren();
            for (PsiElement child : children) {
                appendElement(ifStatementString, child, renameBreakElements,
                        breakLabelString);
            }
        } else {
            final String text = element.getText();
            ifStatementString.append(text);
        }
    }
}