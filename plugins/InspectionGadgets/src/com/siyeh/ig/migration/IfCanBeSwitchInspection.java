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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.DocumentAdapter;
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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class IfCanBeSwitchInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public int minimumBranches = 3;

    @SuppressWarnings({"PublicField"})
    public boolean suggestIntSwitches = false;

    @SuppressWarnings({"PublicField"})
    public boolean suggestEnumSwitches = false;

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

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
        return new IfCanBeSwitchFix(minimumBranches);
    }

    @Override
    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JLabel label = new JLabel(InspectionGadgetsBundle.message(
                "if.can.be.switch.minimum.branch.option"));
        final NumberFormat formatter = NumberFormat.getIntegerInstance();
        formatter.setParseIntegerOnly(true);
        final JFormattedTextField valueField =
                new JFormattedTextField(formatter);
        valueField.setValue(Integer.valueOf(minimumBranches));
        valueField.setColumns(2);
        final Document document = valueField.getDocument();
        document.addDocumentListener(new DocumentAdapter() {
            @Override
            public void textChanged(DocumentEvent e) {
                try {
                    valueField.commitEdit();
                    minimumBranches =
                            ((Number) valueField.getValue()).intValue();
                } catch (ParseException e1) {
                    // No luck this time
                }
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets.left = 4;
        constraints.insets.top = 4;
        constraints.weightx = 0.0;
        constraints.anchor = GridBagConstraints.BASELINE_LEADING;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        panel.add(valueField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 2;
        final JCheckBox checkBox1 = new JCheckBox(
                InspectionGadgetsBundle.message("if.can.be.switch.int.option"),
                suggestIntSwitches);
        final ButtonModel model1 = checkBox1.getModel();
        model1.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                suggestIntSwitches = model1.isSelected();
            }
        });
        panel.add(checkBox1, constraints);
        constraints.gridy = 2;
        constraints.weighty = 1.0;
        final JCheckBox checkBox2 = new JCheckBox(
                InspectionGadgetsBundle.message("if.can.be.switch.enum.option"),
                suggestEnumSwitches);
        final ButtonModel model2 = checkBox2.getModel();
        model2.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                suggestEnumSwitches = model2.isSelected();
            }
        });
        panel.add(checkBox2, constraints);
        return panel;
    }

    private static class IfCanBeSwitchFix extends InspectionGadgetsFix {
      private final int myMinimumBranches;

      public IfCanBeSwitchFix(int minimumBranches) {
        myMinimumBranches = minimumBranches;
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
            final PsiExpression switchExpression =
                    SwitchUtils.getSwitchExpression(ifStatement,
                            myMinimumBranches);
            if (switchExpression == null) {
                return;
            }
            while (true) {
                final PsiExpression condition = ifStatement.getCondition();
                final PsiStatement thenBranch = ifStatement.getThenBranch();
                final IfStatementBranch ifBranch =
                        new IfStatementBranch(thenBranch, false);
                extractCaseExpressions(condition, switchExpression, ifBranch);
                if (!branches.isEmpty()) {
                    extractIfComments(ifStatement, ifBranch);
                }
                extractStatementComments(thenBranch, ifBranch);
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
            final PsiType type = switchExpression.getType();
            final boolean castToInt = type != null &&
                    type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER);
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
                dumpBranch(branch, castToInt, hasConflicts, breaksNeedRelabeled,
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

        private static void extractCaseExpressions(
                PsiExpression expression, PsiExpression switchExpression,
                IfStatementBranch values) {
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
                if (EquivalenceChecker.expressionsAreEquivalent(switchExpression,
                        argument)) {
                    values.addCaseExpression(qualifierExpression);
                } else {
                    values.addCaseExpression(argument);
                }
            } else if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)expression;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                if (JavaTokenType.OROR.equals(tokenType)) {
                    extractCaseExpressions(lhs, switchExpression,
                            values);
                    extractCaseExpressions(rhs, switchExpression,
                            values);
                } else {
                    if (EquivalenceChecker.expressionsAreEquivalent(
                            switchExpression, rhs)) {
                        values.addCaseExpression(lhs);
                    } else {
                        values.addCaseExpression(rhs);
                    }
                }
            } else if (expression instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression =
                        (PsiParenthesizedExpression)expression;
                final PsiExpression contents =
                        parenthesizedExpression.getExpression();
                extractCaseExpressions(contents, switchExpression, values);
            }
        }

        private static void dumpBranch(
                IfStatementBranch branch, boolean castToInt, boolean wrap,
                boolean renameBreaks, String breakLabelName,
                @NonNls StringBuilder switchStatementText) {
            dumpComments(branch.getComments(), switchStatementText);
            if (branch.isElse()) {
                switchStatementText.append("default: ");
            } else {
                for (PsiExpression caseExpression : branch.getConditions()) {
                    switchStatementText.append("case ");
                    switchStatementText.append(getCaseLabelText(caseExpression,
                            castToInt));
                    switchStatementText.append(": ");
                }
            }
            dumpComments(branch.getStatementComments(), switchStatementText);
            dumpBody(branch.getStatement(), wrap, renameBreaks, breakLabelName,
                    switchStatementText);
        }

        @NonNls
        private static String getCaseLabelText(PsiExpression expression,
                                               boolean castToInt) {
            if (expression instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) expression;
                final PsiElement target = referenceExpression.resolve();
                if (target instanceof PsiEnumConstant) {
                    final PsiEnumConstant enumConstant =
                            (PsiEnumConstant) target;
                    return enumConstant.getName();
                }
            }
            if (castToInt) {
                final PsiType type = expression.getType();
                if (!PsiType.INT.equals(type)) {
                    /*
                    because
                    Integer a = 1;
                    switch (a) {
                        case (byte)7:
                    }
                    does not compile with javac (but does with Eclipse)
                     */
                    return "(int)" + expression.getText();
                }
            }
            return expression.getText();
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
                    switchStatementText.append(text);
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
            final PsiType type = switchExpression.getType();
            if (!suggestIntSwitches) {
                if (type instanceof PsiClassType) {
                    if (type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER) ||
                            type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) ||
                            type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
                            type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER)) {
                        return;
                    }
                } else if (PsiType.INT.equals(type) ||
                        PsiType.SHORT.equals(type) ||
                        PsiType.BYTE.equals(type) ||
                        PsiType.CHAR.equals(type)) {
                    return;
                }
            }
            if (!suggestEnumSwitches && type instanceof PsiClassType) {
                final PsiClassType classType = (PsiClassType) type;
                final PsiClass aClass = classType.resolve();
                if (aClass == null || aClass.isEnum()) {
                    return;
                }
            }
            registerStatementError(statement, switchExpression);
        }
    }
}
