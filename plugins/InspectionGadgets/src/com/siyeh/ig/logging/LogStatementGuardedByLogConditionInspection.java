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

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jdom.Element;

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
    private Map<String, String> loggerMethodAndconditionMethodTextMap =
           new HashMap();


    public LogStatementGuardedByLogConditionInspection() {
        initSettings();
    }

    public void readSettings(Element element) throws InvalidDataException {
        super.readSettings(element);
        initSettings();
    }

    private void initSettings() {
        final List<String> loggerMethodNames = new ArrayList();
        final List<String> conditionMethodNames = new ArrayList();
        parseString(loggerMethodAndconditionMethodNames, loggerMethodNames,
                conditionMethodNames);
        for (int i = 0; i < loggerMethodNames.size(); i++) {
            final String loggerMethodName = loggerMethodNames.get(i);
            final String conditionMethodName = conditionMethodNames.get(i);
            loggerMethodAndconditionMethodTextMap.put(loggerMethodName,
                    conditionMethodName);
        }
    }

    public void writeSettings(Element element) throws WriteExternalException {
        final Set<String> loggerMethodNames =
                loggerMethodAndconditionMethodTextMap.keySet();
        final Collection<String> conditionMethodNames =
                loggerMethodAndconditionMethodTextMap.values();
        loggerMethodAndconditionMethodNames = formatString(loggerMethodNames,
                conditionMethodNames);
        super.writeSettings(element);
    }

    @NotNull
    public String getDisplayName() {
        return "Log statement not guarded by log condition";
        //return InspectionGadgetsBundle.message(
        //        "logger.initialized.with.foreign.class.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return "<code>#ref()</code> log statement not guarded by log condition";
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new LogStatementGuardedByLogConditionFix();
    }

    private class LogStatementGuardedByLogConditionFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return "Surround with log condition";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) element.getParent();
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
            final StringBuilder ifStatementText = new StringBuilder("if (");
            ifStatementText.append(qualifier.getText());
            ifStatementText.append('.');
            final String conditionMethodText =
                    loggerMethodAndconditionMethodTextMap.get(referenceName);
            ifStatementText.append(conditionMethodText);
            ifStatementText.append(") {}");
            final PsiStatement ifStatement = factory.createStatementFromText(
                    ifStatementText.toString(), statement);
            final PsiElement[] children = ifStatement.getChildren();
            final PsiBlockStatement blockStatement =
                    (PsiBlockStatement) children[0];
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            for (PsiStatement logStatement : logStatements) {
                codeBlock.add(logStatement);
            }
            final PsiStatement firstStatement = logStatements.get(0);
            final PsiElement parent = firstStatement.getParent();
            parent.addBefore(ifStatement, firstStatement);
            //for (PsiStatement logStatement : logStatements) {
            //    logStatement.delete();
            //}
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
            if (!loggerMethodAndconditionMethodTextMap.containsKey(
                    referenceName)) {
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
            if (isSurroundByLogGuard(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private boolean isSurroundByLogGuard(
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
            final PsiReferenceExpression reference =
                    methodCallExpression.getMethodExpression();
            final PsiType referenceType = reference.getType();
            if (referenceType == null) {
                return false;
            }
            return referenceType.equalsToText(loggerClassName);
        }

    }

    private class Form {

    }
}
