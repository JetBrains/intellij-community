/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TestMethodWithoutAssertionInspection extends BaseInspection {

    /** @noinspection PublicField*/
    @NonNls public String assertionMethods =
            "org.junit.Assert,assert.*|fail.*," +
                    "junit.framework.Assert,assert.*|fail.*," +
                    "org.mockito.Mockito,verify.*";

    private final List<String> methodNamePatterns = new ArrayList();
    private final List<String> classNames = new ArrayList();
    private Map<String, Pattern> patternCache = null;

    @SuppressWarnings({"PublicField"})
    public boolean assertKeywordIsAssertion = false;

    public TestMethodWithoutAssertionInspection() {
        parseString(assertionMethods, classNames, methodNamePatterns);
    }

    @Override
    @NotNull
    public String getID() {
        return "JUnitTestMethodWithNoAssertions";
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "test.method.without.assertion.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "test.method.without.assertion.problem.descriptor");
    }

    @Override
    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final ListTable table = new ListTable(new ListWrappingTableModel(
                Arrays.asList(classNames, methodNamePatterns),
                InspectionGadgetsBundle.message("class.name"),
                InspectionGadgetsBundle.message("method.name.pattern")));
        final JScrollPane scrollPane =
                ScrollPaneFactory.createScrollPane(table);

        final ActionToolbar toolbar =
                UiUtils.createAddRemoveToolbar(table);

        final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message(
                        "assert.keyword.is.considered.an.assertion"), this,
                "assertKeywordIsAssertion");

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(toolbar.getComponent(), constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, constraints);

        constraints.gridy = 2;
        constraints.weighty = 0.0;
        panel.add(checkBox, constraints);
        return panel;
    }

    @Override
    public void readSettings(Element element) throws InvalidDataException {
        super.readSettings(element);
        parseString(assertionMethods, classNames, methodNamePatterns);
    }

    @Override
    public void writeSettings(Element element) throws WriteExternalException {
        assertionMethods = formatString(classNames, methodNamePatterns);
        super.writeSettings(element);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TestMethodWithoutAssertionVisitor();
    }

    private class TestMethodWithoutAssertionVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (!TestUtils.isJUnitTestMethod(method)) {
                return;
            }
            if (hasExpectedExceptionAnnotation(method)) {
                return;
            }
            if (containsAssertion(method)) {
                return;
            }
            if (lastStatementIsCallToMethodWithAssertion(method)) {
                return;
            }
            registerMethodError(method);
        }

        private boolean lastStatementIsCallToMethodWithAssertion(
                PsiMethod method) {
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return false;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements.length <= 0) {
                return false;
            }
            final PsiStatement lastStatement = statements[0];
            if (!(lastStatement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement) lastStatement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) expression;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (qualifierExpression != null &&
                    !(qualifierExpression instanceof PsiThisExpression)) {
                return false;
            }
            final PsiMethod targetMethod = methodCallExpression.resolveMethod();
            return containsAssertion(targetMethod);
        }

        private boolean containsAssertion(PsiElement element) {
          if (element == null) return false;
            final ContainsAssertionVisitor visitor =
                    new ContainsAssertionVisitor();
            element.accept(visitor);
            return visitor.containsAssertion();
        }

        private boolean hasExpectedExceptionAnnotation(PsiMethod method) {
            final PsiModifierList modifierList = method.getModifierList();
            final PsiAnnotation testAnnotation =
                    modifierList.findAnnotation("org.junit.Test");
            if (testAnnotation == null) {
                return false;
            }
            final PsiAnnotationParameterList parameterList =
                    testAnnotation.getParameterList();
            final PsiNameValuePair[] nameValuePairs =
                    parameterList.getAttributes();
            for (PsiNameValuePair nameValuePair : nameValuePairs) {
                @NonNls final String parameterName = nameValuePair.getName();
                if ("expected".equals(parameterName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private class ContainsAssertionVisitor
            extends JavaRecursiveElementVisitor {

        private boolean containsAssertion = false;

        @Override public void visitElement(@NotNull PsiElement element) {
            if (!containsAssertion) {
                super.visitElement(element);
            }
        }

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call) {
            if (containsAssertion) {
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (methodName == null) {
                return;
            }
            final int methodNamesSize = methodNamePatterns.size();
            for (int i = 0; i < methodNamesSize; i++) {
                final String pattern = methodNamePatterns.get(i);
                if (!methodNamesMatch(methodName, pattern)) {
                    continue;
                }
                final PsiMethod method = call.resolveMethod();
                if (method == null || method.isConstructor()) {
                    continue;
                }
                final PsiClass aClass = method.getContainingClass();
                if (!InheritanceUtil.isInheritor(aClass, classNames.get(i))) {
                    continue;
                }
                containsAssertion = true;
                break;
            }
        }

        @Override
        public void visitAssertStatement(PsiAssertStatement statement) {
            if (containsAssertion) {
                return;
            }
            super.visitAssertStatement(statement);
            if (!assertKeywordIsAssertion) {
                return;
            }
            containsAssertion = true;
        }

        public boolean containsAssertion() {
            return containsAssertion;
        }
    }

    private boolean methodNamesMatch(String methodName,
                                     String methodNamePattern){
        Pattern pattern;
        if (patternCache != null) {
            pattern = patternCache.get(methodNamePattern);
        } else {
            patternCache = new HashMap(methodNamePatterns.size());
            pattern = null;
        }
        if (pattern == null) {
            try {
                pattern = Pattern.compile(methodNamePattern);
                patternCache.put(methodNamePattern, pattern);
            } catch (PatternSyntaxException ignore) {
                return false;
            } catch (NullPointerException ignore) {
                return false;
            }
        }
        if (pattern == null) {
            return false;
        }
        final Matcher matcher = pattern.matcher(methodName);
        return matcher.matches();
    }
}