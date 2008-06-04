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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.IteratorUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class KeySetIterationMayUseEntrySetInspection extends BaseInspection {

    @NotNull @Nls
    public String getDisplayName() {
        return "Iteration over key set may be replaced by entry set iteration";
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return "Iteration over key set <code>#ref</code> may be replaced by entry set iteration";
    }

    public BaseInspectionVisitor buildVisitor() {
        Map<String, String> map = new HashMap();
        final Set<String> keySet = map.keySet();
        for (Iterator<String> it = keySet.iterator(); it.hasNext();) {
            String key = it.next();
            final String value = map.get(key);
//            map = new HashMap();
        }
        for (String s : map.keySet()) {
            map.get(s);
        }
        final Iterator<String> it = keySet.iterator();
        while (it.hasNext()) {
            final String key = it.next();
            map.get(key);
        }
        final Set<Map.Entry<String, String>> entries = map.entrySet();
        return new KeySetIterationMayUseEntrySetVisitor();
    }

    private static class KeySetIterationMayUseEntrySetVisitor
            extends BaseInspectionVisitor {

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if (!(condition instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) condition;
            if (!IteratorUtils.isCallToHasNext(methodCallExpression)) {
                return;
            }
            final PsiStatement initialization = statement.getInitialization();
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return;
            }
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement) initialization;
            final PsiElement[] declaredElements =
                    declarationStatement.getDeclaredElements();
            if (declaredElements.length != 1) {
                return;
            }
            final PsiElement element = declaredElements[0];
            if (!(element instanceof PsiLocalVariable)) {
                return;
            }
            final PsiVariable variable = (PsiVariable) element;
            final PsiExpression initializer = variable.getInitializer();
            if (!TypeUtils.expressionHasType("java.util.Iterator",
                    initializer)) {
                return;
            }
            if (initializer instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression expression =
                        (PsiMethodCallExpression) initializer;
                final PsiReferenceExpression methodExpression =
                        expression.getMethodExpression();
                final String name = methodExpression.getReferenceName();
                if (!"iterator".equals(name)) {
                    return;
                }

            } // else variable
        }

        @Nullable private PsiMethodCallExpression getMethodCallFromExpression(
                PsiExpression expression) {
            if (expression instanceof PsiMethodCallExpression) {
                return (PsiMethodCallExpression) expression;
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return null;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) expression;
            final PsiElement element = referenceExpression.resolve();
            if (!(element instanceof PsiVariable)) {
                return null;
            }
            final PsiVariable variable = (PsiVariable) element;
            final PsiExpression initializer = variable.getInitializer();
            return getMethodCallFromExpression(initializer);
        }

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
                    statement)) {
                return;
            }
            registerError(iteratedValue);
        }

        private static boolean isMapKeySetIteration(
                PsiExpression iteratedExpression,
                PsiVariable key,
                PsiElement context) {
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
            if (!classType.equalsToText("java.util.Map")) {
                return false;
            }
            final GetValueFromMapChecker checker =
                    new GetValueFromMapChecker(targetVariable, key);
            context.accept(checker);
            return checker.isGetValueFromMap();
        }

    }

    private static class GetValueFromMapChecker
            extends PsiRecursiveElementVisitor {

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
