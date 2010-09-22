/*
 * Copyright 2008 Bas Leijdekkers
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
package com.siyeh.ig.logging;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.RemoveAction;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.codeInspection.ui.AddAction;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jdom.Element;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.text.Document;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.util.*;

public class LogStatementGuardedByLogConditionInspection
        extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public String loggerClassName = "java.util.logging.Logger";
    @SuppressWarnings({"PublicField"})
    public String loggerMethodAndconditionMethodNames =
            "fine,isLoggable(java.util.logging.Level.FINE)," +
                    "finer,isLoggable(java.util.logging.Level.FINER)," +
                    "finest,isLoggable(java.util.logging.Level.FINEST)";
    private final List<String> logMethodNameList = new ArrayList();
    private final List<String> logConditionMethodNameList = new ArrayList();

    public LogStatementGuardedByLogConditionInspection() {
        parseString(loggerMethodAndconditionMethodNames, logMethodNameList,
                logConditionMethodNameList);
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "log.statement.guarded.by.log.condition.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "log.statement.guarded.by.log.condition.problem.descriptor");
    }

    @Override
    public JComponent createOptionsPanel() {
        return new Form().getContentPanel();
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new LogStatementGuardedByLogConditionFix();
    }

    private class LogStatementGuardedByLogConditionFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "log.statement.guarded.by.log.condition.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) element.getParent().getParent();
            final PsiStatement statement = PsiTreeUtil.getParentOfType(
                    methodCallExpression, PsiStatement.class);
            if (statement == null) {
                return;
            }
            final List<PsiStatement> logStatements = new ArrayList();
            logStatements.add(statement);
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final String referenceName = methodExpression.getReferenceName();
            if (referenceName == null) {
                return;
            }
            PsiStatement previousStatement =
                    PsiTreeUtil.getPrevSiblingOfType(statement,
                            PsiStatement.class);
            while (previousStatement != null &&
                    isSameLogMethodCall(previousStatement, referenceName)) {
                logStatements.add(0, previousStatement);
                previousStatement = PsiTreeUtil.getPrevSiblingOfType(
                        previousStatement, PsiStatement.class);
            }
            PsiStatement nextStatement =
                    PsiTreeUtil.getNextSiblingOfType(statement,
                            PsiStatement.class);
            while (nextStatement != null &&
                    isSameLogMethodCall(nextStatement, referenceName)) {
                logStatements.add(nextStatement);
                nextStatement = PsiTreeUtil.getNextSiblingOfType(
                        nextStatement, PsiStatement.class);
            }
            final PsiElementFactory factory =
                    JavaPsiFacade.getInstance(project).getElementFactory();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final StringBuilder ifStatementText = new StringBuilder("if (");
            ifStatementText.append(qualifier.getText());
            ifStatementText.append('.');
            final int index = logMethodNameList.indexOf(referenceName);
            final String conditionMethodText =
                    logConditionMethodNameList.get(index);
            ifStatementText.append(conditionMethodText);
            ifStatementText.append(") {}");
            final PsiIfStatement ifStatement =
                    (PsiIfStatement)factory.createStatementFromText(
                            ifStatementText.toString(), statement);
            final PsiBlockStatement blockStatement =
                    (PsiBlockStatement)ifStatement.getThenBranch();
            if (blockStatement == null) {
                return;
            }
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            for (PsiStatement logStatement : logStatements) {
                codeBlock.add(logStatement);
            }
            final PsiStatement firstStatement = logStatements.get(0);
            final PsiElement parent = firstStatement.getParent();
            final PsiElement result = parent.addBefore(ifStatement,
                    firstStatement);
            final JavaCodeStyleManager codeStyleManager =
                    JavaCodeStyleManager.getInstance(project);
            codeStyleManager.shortenClassReferences(result);
            for (PsiStatement logStatement : logStatements) {
                logStatement.delete();
            }
        }

        private boolean isSameLogMethodCall(PsiStatement statement,
                                            @NotNull String methodName) {
            if (statement == null) {
                return false;
            }
            if (!(statement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement) statement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) expression;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final String referenceName = methodExpression.getReferenceName();
            if (!methodName.equals(referenceName)) {
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            final PsiType type = qualifier.getType();
            return type != null && type.equalsToText(loggerClassName);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LogStatementGuardedByLogConditionVisitor();
    }

    private class LogStatementGuardedByLogConditionVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String referenceName = methodExpression.getReferenceName();
            if (!logMethodNameList.contains(referenceName)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final PsiType type = qualifier.getType();
            if (type == null) {
                return;
            }
            if (!type.equalsToText(loggerClassName)) {
                return;
            }
            if (isSurroundedByLogGuard(expression)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                return;
            }
            final PsiExpression firstArgument = arguments[0];
            if (PsiUtil.isConstantExpression(firstArgument)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private boolean isSurroundedByLogGuard(
                PsiMethodCallExpression expression) {
            final PsiIfStatement ifStatement =
                    PsiTreeUtil.getParentOfType(expression,
                            PsiIfStatement.class);
            if (ifStatement == null) {
                return false;
            }
            final PsiExpression condition = ifStatement.getCondition();
            if (!(condition instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) condition;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            final PsiType qualifierType = qualifier.getType();
            return qualifierType != null && qualifierType.equalsToText(
                    loggerClassName);
        }

    }

    public void readSettings(Element element) throws InvalidDataException {
        super.readSettings(element);
        parseString(loggerMethodAndconditionMethodNames, logMethodNameList,
                logConditionMethodNameList);
    }

    public void writeSettings(Element element) throws WriteExternalException {
        loggerMethodAndconditionMethodNames = formatString(logMethodNameList,
                logConditionMethodNameList);
        super.writeSettings(element);
    }

    class Form {

        private JPanel contentPanel;
        private JTextField loggerClassNameTextField;
        private ListTable table;
        private JButton addButton;
        private JButton removeButton;

        Form() {
            loggerClassNameTextField.setText(loggerClassName);
            final DocumentListener listener = new DocumentListener() {

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
                    loggerClassName =  loggerClassNameTextField.getText();
                }
            };
            final Document document = loggerClassNameTextField.getDocument();
            document.addDocumentListener(listener);
            addButton.setAction(new AddAction(table));
            removeButton.setAction(new RemoveAction(table));
        }

        public JPanel getContentPanel() {
            return contentPanel;
        }

        private void createUIComponents() {
            table = new ListTable(new ListWrappingTableModel(
                    Arrays.asList(logMethodNameList, logConditionMethodNameList),
                    "log method name",
                    "log condition text"));
        }
    }
}
