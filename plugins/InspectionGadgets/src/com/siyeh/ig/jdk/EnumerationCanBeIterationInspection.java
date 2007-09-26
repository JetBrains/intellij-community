/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.jdk;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnumerationCanBeIterationInspection extends BaseInspection {

    @NonNls
    static final String ITERATOR_TEXT = "iterator()";

    @NonNls
    static final String KEY_SET_ITERATOR_TEXT = "keySet().iterator()";

    @NonNls
    static final String VALUES_ITERATOR_TEXT = "values().iterator()";

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "enumeration.can.be.iteration.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "enumeration.can.be.iteration.problem.descriptor", infos[0]);
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new EnumerationCanBeIterationFix();
    }

    private static class EnumerationCanBeIterationFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "enumeration.can.be.iteration.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    (PsiReferenceExpression)element.getParent();
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)methodExpression.getParent();
            final PsiElement parent =
                    methodCallExpression.getParent();
            final PsiVariable variable;
            final PsiAssignmentExpression assignmentExpression;
            if (parent instanceof PsiVariable) {
                variable = (PsiVariable) parent;
                assignmentExpression = null;
            } else if (parent instanceof PsiAssignmentExpression) {
                assignmentExpression = (PsiAssignmentExpression) parent;
                final PsiExpression lhs = assignmentExpression.getLExpression();
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) lhs;
                final PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiVariable)) {
                    return;
                }
                variable = (PsiVariable) target;
            } else {
                return;
            }
            final String variableName = createVariableName(element);
            final PsiStatement statement = PsiTreeUtil.getParentOfType(element,
                    PsiStatement.class);
            if (statement == null) {
                return;
            }
            final boolean deleteInitialization =
                    replaceMethodCalls(variable, statement.getTextOffset(),
                            variableName);
            final PsiStatement newStatement =
                    createDeclaration(methodCallExpression, variableName);
            if (newStatement == null) {
                return;
            }
            final PsiElement statementParent = statement.getParent();
            statementParent.addAfter(newStatement, statement);
            if (deleteInitialization) {
                if (assignmentExpression == null) {
                    variable.delete();
                } else {
                    assignmentExpression.delete();
                }
            }
        }

        @Nullable
        private static PsiStatement createDeclaration(
                PsiMethodCallExpression methodCallExpression,
                String variableName)
                throws IncorrectOperationException {
            final StringBuilder newStatementText =
                    new StringBuilder();
            final Project project = methodCallExpression.getProject();
            final CodeStyleSettings codeStyleSettings =
                    CodeStyleSettingsManager.getSettings(project);
            if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
                newStatementText.append("final ");
            }
            newStatementText.append("java.util.Iterator ");
            newStatementText.append(variableName);
            newStatementText.append('=');
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            final String qualifierText;
            if (qualifier == null) {
                qualifierText = "";
            } else {
                qualifierText = qualifier.getText() + '.';
            }
            newStatementText.append(qualifierText);
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if ("elements".equals(methodName)) {
                if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        "java.util.Vector")) {
                    newStatementText.append(ITERATOR_TEXT);
                } else if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        "java.util.Hashtable")) {
                    newStatementText.append(VALUES_ITERATOR_TEXT);
                } else {
                    return null;
                }
            } else if ("keys".equals(methodName)) {
                if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        "java.util.Hashtable")) {
                    newStatementText.append(KEY_SET_ITERATOR_TEXT);
                } else {
                    return null;
                }
            } else {
                return null;
            }
            newStatementText.append(';');
            final PsiManager manager = methodCallExpression.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiStatement statement =
                    factory.createStatementFromText(newStatementText.toString(),
                            methodExpression);
            final CodeStyleManager styleManager = manager.getCodeStyleManager();
            styleManager.shortenClassReferences(statement);
            return statement;
        }

        /** @return true if the initialization of the Enumeration variable can
         *  be deleted. */
        private static boolean replaceMethodCalls(
                PsiVariable enumerationVariable,
                int startOffset,
                String newVariableName)
                throws IncorrectOperationException {
            final PsiManager manager = enumerationVariable.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            boolean deleteInitialization = true;
            final Query<PsiReference> query = ReferencesSearch.search(
                    enumerationVariable);
            for (PsiReference reference : query) {
                final PsiElement referenceElement = reference.getElement();
                if (!(referenceElement instanceof PsiReferenceExpression)) {
                    deleteInitialization = false;
                    continue;
                }
                if (referenceElement.getTextOffset() <=
                        startOffset) {
                    continue;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) referenceElement;
                final PsiElement referenceParent =
                        referenceExpression.getParent();
                if (!(referenceParent instanceof PsiReferenceExpression)) {
                    if (referenceParent instanceof PsiAssignmentExpression) {
                        break;
                    }
                    deleteInitialization = false;
                    continue;
                }
                final PsiElement referenceGrandParent =
                        referenceParent.getParent();
                if (!(referenceGrandParent instanceof PsiMethodCallExpression)) {
                    deleteInitialization = false;
                    continue;
                }
                final PsiMethodCallExpression callExpression =
                        (PsiMethodCallExpression) referenceGrandParent;
                final PsiReferenceExpression foundReferenceExpression =
                        callExpression.getMethodExpression();
                final String foundName =
                        foundReferenceExpression.getReferenceName();
                final String newExpressionText;
                if ("hasMoreElements".equals(foundName)) {
                    newExpressionText = newVariableName + ".hasNext()";
                } else if ("nextElement".equals(foundName)) {
                    newExpressionText = newVariableName + ".next()";
                } else {
                    deleteInitialization = false;
                    continue;
                }
                final PsiExpression newExpression =
                        factory.createExpressionFromText(newExpressionText,
                                callExpression);
                callExpression.replace(newExpression);
            }
            return deleteInitialization;
        }

        private static String createVariableName(PsiElement context) {
            final PsiManager manager = context.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final Project project = context.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiClass iteratorClass =
                    manager.findClass("java.util.Iterator", scope);
            if (iteratorClass == null) {
                return "iterator";
            }
            final CodeStyleManager codeStyleManager =
                    manager.getCodeStyleManager();
            final PsiType iteratorType = factory.createType(iteratorClass);
            final SuggestedNameInfo baseNameInfo =
                    codeStyleManager.suggestVariableName(
                            VariableKind.LOCAL_VARIABLE, null, null,
                            iteratorType);
            final SuggestedNameInfo nameInfo =
                    codeStyleManager.suggestUniqueVariableName(baseNameInfo,
                            context, true);
            if (nameInfo.names.length <= 0) {
                return "iterator";
            }
            System.out.println("nameInfo: " + nameInfo);
            return nameInfo.names[0];
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EnumerationCanBeIterationVisitor();
    }

    private static class EnumerationCanBeIterationVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            final boolean isElements;
            if ("elements".equals(methodName)) {
                isElements = true;
            } else if ("keys".equals(methodName)) {
                isElements = false;
            } else {
                return;
            }
            if (!TypeUtils.expressionHasTypeOrSubtype(expression,
                    "java.util.Enumeration")) {
                return;
            }
            final PsiElement parent = expression.getParent();
            final PsiVariable variable;
            if (parent instanceof PsiLocalVariable) {
                variable = (PsiVariable) parent;
            } else if (parent instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression assignmentExpression =
                        (PsiAssignmentExpression) parent;
                final PsiExpression lhs = assignmentExpression.getLExpression();
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) lhs;
                final PsiElement element = referenceExpression.resolve();
                if (!(element instanceof PsiVariable)) {
                    return;
                }
                variable = (PsiVariable) element;
            } else {
                return;
            }
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(
                    expression, PsiMethod.class);
            if (containingMethod == null) {
                return;
            }
            if (!isEnumerationMethodCalled(variable, containingMethod)) {
                return;
            }
            if (isElements) {
                final PsiMethod method = expression.resolveMethod();
                if (method == null) {
                    return;
                }
                final PsiClass containingClass = method.getContainingClass();
                if (ClassUtils.isSubclass(containingClass,
                        "java.util.Vector")) {
                    registerMethodCallError(expression, ITERATOR_TEXT);
                } else if (ClassUtils.isSubclass(containingClass,
                        "java.util.Hashtable")) {
                    registerMethodCallError(expression, VALUES_ITERATOR_TEXT);
                }
            } else {
                final PsiMethod method = expression.resolveMethod();
                if (method == null) {
                    return;
                }
                final PsiClass containingClass = method.getContainingClass();
                if (ClassUtils.isSubclass(containingClass,
                        "java.util.Hashtable")) {
                    registerMethodCallError(expression, KEY_SET_ITERATOR_TEXT);
                }
            }
        }

        private static boolean isEnumerationMethodCalled(
                @NotNull PsiVariable variable, @NotNull PsiElement context) {
            final EnumerationMethodCalledVisitor visitor =
                    new EnumerationMethodCalledVisitor(variable);
            context.accept(visitor);
            return visitor.isEnumerationMethodCalled();
        }

        private static class EnumerationMethodCalledVisitor
                extends PsiRecursiveElementVisitor {

            private final PsiVariable variable;
            private boolean enumerationMethodCalled = false;

            EnumerationMethodCalledVisitor(@NotNull PsiVariable variable) {
                this.variable = variable;
            }

            public void visitMethodCallExpression(
                    PsiMethodCallExpression expression) {
                if (enumerationMethodCalled) {
                    return;
                }
                super.visitMethodCallExpression(expression);
                final PsiReferenceExpression methodExpression =
                        expression.getMethodExpression();
                final String methodName = methodExpression.getReferenceName();
                if (!"hasMoreElements".equals(methodName) &&
                        !"nextElement".equals(methodName)) {
                    return;
                }
                final PsiExpression qualifierExpression =
                        methodExpression.getQualifierExpression();
                if (!(qualifierExpression instanceof PsiReferenceExpression)) {
                    return;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) qualifierExpression;
                final PsiElement element = referenceExpression.resolve();
                if (!(element instanceof PsiVariable)) {
                    return;
                }
                final PsiVariable variable = (PsiVariable) element;
                enumerationMethodCalled = this.variable.equals(variable);
            }

            public boolean isEnumerationMethodCalled() {
                return enumerationMethodCalled;
            }
        }
    }
}