/*
 * Copyright 2011 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class IfCanBeSwitchInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public int minimumBranches = 3;

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("if.can.be.switch.display.name");
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "if.can.be.switch.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new IfCanBeSwitchFix((PsiExpression) infos[0]);
    }

    @Override
    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel(
                InspectionGadgetsBundle.message(
                        "if.can.be.switch.minimum.branch.option"),
                this, "minimumBranches");
    }

    private static class IfCanBeSwitchFix extends InspectionGadgetsFix {

        private final PsiExpression switchExpression;

        public IfCanBeSwitchFix(PsiExpression switchExpression) {
            this.switchExpression = switchExpression;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message("if.can.be.switch.quickfix");
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement().getParent();
            if (!(element instanceof PsiIfStatement)) {
                return;
            }
            PsiIfStatement ifStatement = (PsiIfStatement)element;
            boolean breaksNeedRelabeled = false;
            PsiStatement breakTarget = null;
            String labelString = "";
            if (ControlFlowUtils.statementContainsNakedBreak(ifStatement)) {
                breakTarget = PsiTreeUtil.getParentOfType(ifStatement,
                        PsiLoopStatement.class, PsiSwitchStatement.class);
                if (breakTarget != null) {
                    final PsiElement parent = breakTarget.getParent();
                    if (parent instanceof PsiLabeledStatement) {
                        final PsiLabeledStatement labeledStatement =
                                (PsiLabeledStatement) parent;
                        labelString =
                                labeledStatement.getLabelIdentifier().getText();
                        breakTarget = labeledStatement;
                        breaksNeedRelabeled = true;
                    } else {
                        labelString =
                                SwitchUtils.findUniqueLabelName(ifStatement,
                                        "label");
                        breaksNeedRelabeled = true;
                    }
                }
            }
            final PsiIfStatement statementToReplace = ifStatement;

            final List<IfStatementBranch> branches =
                    new ArrayList<IfStatementBranch>(20);
            while (true) {
                final PsiExpression condition = ifStatement.getCondition();
                final List<PsiExpression> labels =
                        getValuesFromExpression(condition, switchExpression,
                                new ArrayList());
                final PsiStatement thenBranch = ifStatement.getThenBranch();
                final IfStatementBranch ifBranch =
                        new IfStatementBranch(thenBranch, false);
                if (!branches.isEmpty()) {
                    extractIfComments(ifStatement, ifBranch);
                }
                extractStatementComments(thenBranch, ifBranch);
                for (final PsiExpression label : labels) {
                    if (label instanceof PsiReferenceExpression) {
                        final PsiReferenceExpression reference =
                                (PsiReferenceExpression)label;
                        final PsiElement referent = reference.resolve();
                        if (referent instanceof PsiEnumConstant) {
                            final PsiEnumConstant constant =
                                    (PsiEnumConstant)referent;
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
                    final IfStatementBranch elseIfBranch =
                            new IfStatementBranch(elseBranch, true);
                    final PsiKeyword elseKeyword = ifStatement.getElseElement();
                    extractIfComments(elseKeyword, elseIfBranch);
                    extractStatementComments(elseBranch, elseIfBranch);
                    branches.add(elseIfBranch);
                    break;
                }
            }

            @NonNls final StringBuilder switchStatementText =
                    new StringBuilder();
            switchStatementText.append("switch(");
            switchStatementText.append(switchExpression.getText());
            switchStatementText.append("){");
            for (IfStatementBranch branch : branches) {
                boolean hasConflicts = false;
                for (IfStatementBranch testBranch : branches) {
                    if (branch == testBranch) {
                        continue;
                    }
                    if (branch.topLevelDeclarationsConflictWith(testBranch)) {
                        hasConflicts = true;
                    }
                }
                dumpBranch(branch, hasConflicts, breaksNeedRelabeled,
                        labelString, switchStatementText);
            }
            switchStatementText.append('}');
            final JavaPsiFacade psiFacade =
                    JavaPsiFacade.getInstance(element.getProject());
            final PsiElementFactory factory = psiFacade.getElementFactory();
            if (breaksNeedRelabeled) {
                final StringBuilder out = new StringBuilder();
                if (!(breakTarget instanceof PsiLabeledStatement)) {
                    out.append(labelString);
                    out.append(':');
                }
                termReplace(out, breakTarget, statementToReplace,
                        switchStatementText);
                final String newStatementText = out.toString();
                final PsiStatement newStatement =
                        factory.createStatementFromText(newStatementText,
                                element);
                breakTarget.replace(newStatement);
            } else {
                final PsiStatement newStatement =
                        factory.createStatementFromText(
                                switchStatementText.toString(), element);
                statementToReplace.replace(newStatement);
            }
        }

        @Nullable
        public static <T extends PsiElement> T getPrevSiblingOfType(
                @Nullable PsiElement element,
                @NotNull Class<T> aClass,
                @NotNull Class<? extends PsiElement>... stopAt) {
            if (element == null) {
                return null;
            }
            PsiElement sibling = element.getPrevSibling();
            while (sibling != null && !aClass.isInstance(sibling)) {
                for (Class<? extends PsiElement> stopClass : stopAt) {
                    if (stopClass.isInstance(sibling)) {
                        return null;
                    }
                }
                sibling = sibling.getPrevSibling();
            }
            return (T)sibling;
        }

        private static void extractIfComments(PsiElement element,
                                              IfStatementBranch out) {
            PsiComment comment = getPrevSiblingOfType(element,
                    PsiComment.class, PsiStatement.class);
            while (comment != null) {
                final PsiElement sibling = comment.getPrevSibling();
                final String commentText;
                if (sibling instanceof PsiWhiteSpace) {
                    final String whiteSpaceText = sibling.getText();
                    if (whiteSpaceText.startsWith("\n")) {
                        commentText = whiteSpaceText.substring(1) +
                                comment.getText();
                    } else {
                        commentText = comment.getText();
                    }
                } else {
                    commentText = comment.getText();
                }
                out.addComment(commentText);
                comment = getPrevSiblingOfType(comment, PsiComment.class,
                        PsiStatement.class);
            }
        }

        private static void extractStatementComments(PsiElement element,
                                                     IfStatementBranch out) {
            PsiComment comment = getPrevSiblingOfType(element,
                    PsiComment.class, PsiStatement.class, PsiKeyword.class);
            while (comment != null) {
                final PsiElement sibling = comment.getPrevSibling();
                final String commentText;
                if (sibling instanceof PsiWhiteSpace) {
                    final String whiteSpaceText = sibling.getText();
                    if (whiteSpaceText.startsWith("\n")) {
                        commentText = whiteSpaceText.substring(1) +
                                comment.getText();
                    } else {
                        commentText = comment.getText();
                    }
                } else {
                    commentText = comment.getText();
                }
                out.addStatementComment(commentText);
                comment = getPrevSiblingOfType(comment, PsiComment.class,
                        PsiStatement.class, PsiKeyword.class);
            }
        }

        private static void termReplace(
                StringBuilder out, PsiElement target,
                PsiElement replace, StringBuilder stringToReplaceWith) {
            if (target.equals(replace)) {
                out.append(stringToReplaceWith);
            } else if (target.getChildren().length == 0) {
                out.append(target.getText());
            } else {
                final PsiElement[] children = target.getChildren();
                for (final PsiElement child : children) {
                    termReplace(out, child, replace, stringToReplaceWith);
                }
            }
        }

        private static List<PsiExpression> getValuesFromExpression(
                PsiExpression expression, PsiExpression caseExpression,
                List<PsiExpression> values) {
            if (expression instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) expression;
                final PsiExpressionList argumentList =
                        methodCallExpression.getArgumentList();
                final PsiExpression[] arguments = argumentList.getExpressions();
                final PsiExpression argument = arguments[0];
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final PsiExpression qualifierExpression =
                        methodExpression.getQualifierExpression();
                if (EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                        argument)) {
                    values.add(qualifierExpression);
                } else {
                    values.add(argument);
                }
            } else if (expression instanceof PsiBinaryExpression) {
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
                    if (EquivalenceChecker.expressionsAreEquivalent(
                            caseExpression, rhs)) {
                        values.add(lhs);
                    } else {
                        values.add(rhs);
                    }
                }
            } else if (expression instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression =
                        (PsiParenthesizedExpression)expression;
                final PsiExpression contents =
                        parenthesizedExpression.getExpression();
                getValuesFromExpression(contents, caseExpression, values);
            }
            return values;
        }

        private static void dumpBranch(
                IfStatementBranch branch, boolean wrap,
                boolean renameBreaks, String breakLabelName,
                @NonNls StringBuilder switchStatementText) {
            dumpComments(branch.getComments(), switchStatementText);
            if (branch.isElse()) {
                switchStatementText.append("default: ");
            } else {
                for (String label : branch.getConditions()) {
                    switchStatementText.append("case ");
                    switchStatementText.append(label);
                    switchStatementText.append(": ");
                }
            }
            dumpComments(branch.getStatementComments(), switchStatementText);
            dumpBody(branch.getStatement(), wrap, renameBreaks, breakLabelName,
                    switchStatementText);
        }

        private static void dumpComments(List<String> comments,
                                         StringBuilder switchStatementText) {
            if (comments.isEmpty()) {
                return;
            }
            switchStatementText.append('\n');
            for (String comment : comments) {
                switchStatementText.append(comment);
                switchStatementText.append('\n');
            }
        }

        private static void dumpBody(
                PsiStatement bodyStatement, boolean wrap, boolean renameBreaks,
                String breakLabelName,
                @NonNls StringBuilder switchStatementText) {
            if (wrap) {
                switchStatementText.append('{');
            }
            if (bodyStatement instanceof PsiBlockStatement) {
                final PsiCodeBlock codeBlock =
                        ((PsiBlockStatement)bodyStatement).getCodeBlock();
                final PsiElement[] children = codeBlock.getChildren();
                //skip the first and last members, to unwrap the block
                for (int i = 1; i < children.length - 1; i++) {
                    final PsiElement child = children[i];
                    appendElement(switchStatementText, child, renameBreaks,
                            breakLabelName);
                }
            } else {
                appendElement(switchStatementText, bodyStatement,
                        renameBreaks, breakLabelName);
            }
            if (ControlFlowUtils.statementMayCompleteNormally(
                    bodyStatement)) {
                switchStatementText.append("break;");
            }
            if (wrap) {
                switchStatementText.append('}');
            }
        }

        private static void appendElement(
                @NonNls StringBuilder switchStatementText,
                PsiElement element, boolean renameBreakElements,
                String breakLabelString) {
            final String text = element.getText();
            if (!renameBreakElements) {
                switchStatementText.append(text);
            } else if (element instanceof PsiBreakStatement) {
                final PsiIdentifier identifier =
                        ((PsiBreakStatement)element).getLabelIdentifier();
                if (identifier == null) {
                    switchStatementText.append("break ");
                    switchStatementText.append(breakLabelString);
                    switchStatementText.append(';');
                } else {
                    final String identifierText = identifier.getText();
                    if ("".equals(identifierText)) {
                        switchStatementText.append("break ");
                        switchStatementText.append(breakLabelString);
                        switchStatementText.append(';');
                    } else {
                        switchStatementText.append(text);
                    }
                }
            } else if (element instanceof PsiBlockStatement ||
                    element instanceof PsiCodeBlock ||
                    element instanceof PsiIfStatement) {
                final PsiElement[] children = element.getChildren();
                for (final PsiElement child : children) {
                    appendElement(switchStatementText, child,
                            renameBreakElements, breakLabelString);
                }
            } else {
                switchStatementText.append(text);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IfCanBeSwitchVisitor();
    }

    private class IfCanBeSwitchVisitor extends BaseInspectionVisitor {

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                return;
            }
            final PsiExpression switchExpression =
                    SwitchUtils.getSwitchExpression(statement, minimumBranches);
            if (switchExpression == null) {
                return;
            }
            registerStatementError(statement, switchExpression);
        }
    }
}
