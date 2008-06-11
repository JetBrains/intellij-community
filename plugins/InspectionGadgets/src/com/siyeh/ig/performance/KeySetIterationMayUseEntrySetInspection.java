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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.IteratorUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.StringUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class KeySetIterationMayUseEntrySetInspection extends BaseInspection {

    @NotNull @Nls
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "key.set.iteration.may.use.entry.set.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "key.set.iteration.may.use.entry.set.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new KeySetIterationMapUseEntrySetFix();
    }

    private static class KeySetIterationMapUseEntrySetFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "key.set.iteration.may.use.entry.set.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiForeachStatement)) {
                return;
            }
            final PsiElement map;
            if (element instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) element;
                final PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiVariable)) {
                    return;
                }
                final PsiVariable variable = (PsiVariable) target;
                final PsiExpression expression = variable.getInitializer();
                if (!(expression instanceof PsiMethodCallExpression)) {
                    return;
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) expression;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (!(qualifier instanceof PsiReferenceExpression)) {
                    return;
                }
                final PsiReferenceExpression reference =
                        (PsiReferenceExpression) qualifier;
                map = reference.resolve();
                final String qualifierText = qualifier.getText();
                replaceExpression(referenceExpression,
                        qualifierText + ".entrySet()");
            } else if (element instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) element;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (!(qualifier instanceof PsiReferenceExpression)) {
                    return;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) qualifier;
                map = referenceExpression.resolve();
                final String qualifierText = qualifier.getText();
                replaceExpression(methodCallExpression,
                        qualifierText + ".entrySet()");
            } else {
                return;
            }
            final PsiForeachStatement foreachStatement =
                    (PsiForeachStatement) parent;
            final PsiExpression iteratedValue =
                    foreachStatement.getIteratedValue();
            if (iteratedValue == null) {
                return;
            }
            final PsiType type = iteratedValue.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) type;
            final PsiType[] parameterTypes = classType.getParameters();
            if (parameterTypes.length != 1) {
                return;
            }
            final PsiType parameterType = parameterTypes[0];
            final PsiParameter parameter =
                    foreachStatement.getIterationParameter();
            final String variableName =
                    createNewVariableName(foreachStatement, parameterType);
            replaceParameterAccess(parameter, variableName, map,
                    foreachStatement);
            final PsiElementFactory factory =
                    JavaPsiFacade.getInstance(project).getElementFactory();
            final PsiParameter newParameter = factory.createParameter(
                    variableName, parameterType);
            parameter.replace(newParameter);
        }

        private static void replaceParameterAccess(PsiParameter parameter,
                                                   String variableName,
                                                   PsiElement map,
                                                   PsiElement context) {
            context.accept(
                    new ParameterAccessReplacer(parameter, variableName, map));
        }

        private static String createNewVariableName(
                @NotNull PsiElement scope, PsiType type){
            final Project project = scope.getProject();
            final JavaCodeStyleManager codeStyleManager =
                    JavaCodeStyleManager.getInstance(project);
            @NonNls String baseName;
            final SuggestedNameInfo suggestions =
                    codeStyleManager.suggestVariableName(
                            VariableKind.LOCAL_VARIABLE, null, null, type);
            final String[] names = suggestions.names;
            if(names != null && names.length > 0){
                baseName = names[0];
            } else{
                baseName = "entry";
            }
            if(baseName == null || baseName.length() == 0){
                baseName = "entry";
            }
            return codeStyleManager.suggestUniqueVariableName(baseName, scope,
                    true);
        }

        private static class ParameterAccessReplacer
                extends JavaRecursiveElementVisitor {

            private final PsiParameter parameter;
            private final PsiElement map;
            private final String variableName;
            private String parameterName;

            public ParameterAccessReplacer(
                    PsiParameter parameter, String variableName,
                    PsiElement map) {
                this.parameter = parameter;
                parameterName = parameter.getName();
                this.variableName = variableName;
                this.map = map;
            }

            @Override
            public void visitReferenceExpression(
                    PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                if (expression.getQualifierExpression() != null) {
                    return;
                }
                final String expressionText = expression.getText();
                if (!expressionText.equals(parameterName)) {
                    return;
                }
                final PsiElement target = expression.resolve();
                if (!parameter.equals(target)) {
                    return;
                }
                try {
                    if (!replaceValueUsage(expression)) {
                        replaceExpression(expression,
                                variableName + ".getKey()");
                    }
                } catch (IncorrectOperationException e) {
                    throw new RuntimeException(e);
                }
            }

            private boolean replaceValueUsage(
                    PsiReferenceExpression expression)
                    throws IncorrectOperationException {
                final PsiElement parent = expression.getParent();
                if (!(parent instanceof PsiExpressionList)) {
                    return false;
                }
                final PsiElement grandParent = parent.getParent();
                if (!(grandParent instanceof PsiMethodCallExpression)) {
                    return false;
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) grandParent;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final String methodName =
                        methodExpression.getReferenceName();
                if (!"get".equals(methodName)) {
                    return false;
                }
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (!(qualifier instanceof PsiReferenceExpression)) {
                    return false;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) qualifier;
                final PsiElement target2 =
                        referenceExpression.resolve();
                if (!map.equals(target2)) {
                    return false;
                }
                replaceExpression(methodCallExpression,
                        variableName + ".getValue()");
                return true;
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new KeySetIterationMayUseEntrySetVisitor();
    }

    private static class KeySetIterationMayUseEntrySetVisitor
            extends BaseInspectionVisitor {

        public void visitForeachStatement(PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            final PsiExpression iteratedValue = statement.getIteratedValue();
            if (iteratedValue == null) {
                return;
            }
            final PsiExpression iteratedExpression;
            if (iteratedValue instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) iteratedValue;
                final PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiLocalVariable)) {
                    return;
                }
                final PsiVariable variable = (PsiVariable) target;
                final PsiMethod containingMethod =
                        PsiTreeUtil.getParentOfType(variable, PsiMethod.class);
                if (VariableAccessUtils.variableIsAssignedAtPoint(variable,
                        containingMethod, statement)) {
                    return;
                }
                iteratedExpression = variable.getInitializer();
            } else {
                iteratedExpression = iteratedValue;
            }
            final PsiParameter parameter = statement.getIterationParameter();
            if (!isMapKeySetIteration(iteratedExpression, parameter,
                    statement.getBody())) {
                return;
            }
            registerError(iteratedValue);
        }

        private static boolean isMapKeySetIteration(
                PsiExpression iteratedExpression,
                PsiVariable key,
                @Nullable PsiElement context) {
            if (context == null) {
                return false;
            }
            if (!(iteratedExpression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) iteratedExpression;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"keySet".equals(methodName)) {
                return false;
            }
            final PsiExpression expression =
                    methodExpression.getQualifierExpression();
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) expression;
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return false;
            }
            final PsiVariable targetVariable = (PsiVariable) target;
            final PsiType type = targetVariable.getType();
            if (!(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClassType classType = (PsiClassType) type;
            final PsiClass aClass = classType.resolve();
            if (aClass == null) {
                return false;
            }
            final String className = aClass.getQualifiedName();
            if (!"java.util.Map".equals(className)) {
                return false;
            }
            final GetValueFromMapChecker checker =
                    new GetValueFromMapChecker(targetVariable, key);
            context.accept(checker);
            return checker.isGetValueFromMap();
        }
    }

    private static class GetValueFromMapChecker
            extends JavaRecursiveElementVisitor {

        private final PsiVariable key;
        private final PsiVariable map;
        private boolean getValueFromMap = false;
        private boolean assigned = false;

        GetValueFromMapChecker(@NotNull PsiVariable map,
                               @NotNull PsiVariable key) {
            this.map = map;
            this.key = key;
        }

        public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            if (assigned) {
                return;
            }
            super.visitReferenceExpression(expression);
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiAssignmentExpression) {
                final PsiElement target = expression.resolve();
                if (key.equals(target)) {
                    assigned = true;
                } else if (map.equals(target)) {
                    assigned = true;
                }
            } else if (!(parent instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) grandParent;
            final PsiReferenceExpression methodExpression =
                    (PsiReferenceExpression) parent;
            final PsiElement target = expression.resolve();
            if (!map.equals(target)) {
                return;
            }
            final String methodName =
                    methodExpression.getReferenceName();
            if (!"get".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] arguments =
                    argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            if (!(argument instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) argument;
            final PsiElement argumentTarget =
                    referenceExpression.resolve();
            if (!key.equals(argumentTarget)) {
                return;
            }
            getValueFromMap = true;
        }

        public boolean isGetValueFromMap() {
            return getValueFromMap && !assigned;
        }
    }
}
