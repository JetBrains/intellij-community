/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.ui.ScrollPaneFactory;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
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
            final ContainsAssertionVisitor visitor =
                    new ContainsAssertionVisitor();
            method.accept(visitor);
            if (visitor.containsAssertion()) {
                return;
            }
            registerMethodError(method);
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
            for (int i = 0, methodNamesSize = methodNamePatterns.size();
                 i < methodNamesSize; i++) {
                final String pattern = methodNamePatterns.get(i);
                if (!methodNamesMatch(methodName, pattern)) {
                    continue;
                }
                final PsiMethod method = call.resolveMethod();
                if (method == null || method.isConstructor()) {
                    continue;
                }
                final PsiClass aClass = method.getContainingClass();
                if (!ClassUtils.isSubclass(aClass, classNames.get(i))) {
                    continue;
                }
                containsAssertion = true;
                break;
            }
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