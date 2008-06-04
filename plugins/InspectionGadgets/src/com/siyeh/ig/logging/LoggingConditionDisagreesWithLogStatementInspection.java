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

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.jetbrains.annotations.NotNull;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.psi.*;

import java.util.Set;
import java.util.HashSet;

public class LoggingConditionDisagreesWithLogStatementInspection
        extends BaseInspection {

    private static final Set<String> logMethodNames = new HashSet();
    static {
        logMethodNames.add("debug");
        logMethodNames.add("error");
        logMethodNames.add("fatal");
        logMethodNames.add("info");
        logMethodNames.add("log");
        logMethodNames.add("warn");
    }

    enum Priority {
        DEBUG, INFO, WARN, ERROR, FATAL
    }

    @NotNull
    public String getDisplayName() {
        return "Logging condition does not match with log statement";
        //return InspectionGadgetsBundle.message(
        //        "logger.initialized.with.foreign.class.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return "Condition <code>#ref</code> does not match log statement";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoggingConditionDisagreesWithLogStatementVisitor();
    }

    private static class LoggingConditionDisagreesWithLogStatementVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String referenceName = methodExpression.getReferenceName();
            if (!logMethodNames.contains(referenceName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            final String qualifiedName = containingClass.getQualifiedName();
            if (!"org.apache.log4j.Logger".equals(qualifiedName)) {
                return;
            }
            final PsiElement parent = expression.getParent();
            final PsiIfStatement ifStatement;
            if (parent instanceof PsiCodeBlock) {
                final PsiElement grandParent = parent.getParent();
                if (!(grandParent instanceof PsiBlockStatement)) {
                    return;
                }
                final PsiElement greatGrandParent = grandParent.getParent();
                if (!(greatGrandParent instanceof PsiIfStatement)) {
                    return;
                }
                ifStatement = (PsiIfStatement) greatGrandParent;
            } else if (parent instanceof PsiIfStatement) {
                ifStatement = (PsiIfStatement) parent;
            } else {
                return;
            }
            final PsiExpression condition = ifStatement.getCondition();
            if (!(condition instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) qualifier;
            final PsiElement target = referenceExpression.resolve();
            if (target == null) {
                return;
            }

            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) condition;
            final PsiReferenceExpression conditionMethodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression conditionQualifier =
                    conditionMethodExpression.getQualifierExpression();
            if (!(conditionQualifier instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression conditionReferenceExpression =
                    (PsiReferenceExpression) conditionQualifier;
            final PsiElement conditionTarget =
                    conditionReferenceExpression.resolve();
            if (!target.equals(conditionTarget)) {
                return;
            }
            final String methodName =
                    conditionMethodExpression.getReferenceName();
            Priority enabledFor;
            if ("isDebugEnabled".equals(methodName)) {
                enabledFor = Priority.DEBUG;
            } else if ("isInfoEnabled".equals(methodName)) {
                enabledFor = Priority.INFO;
            } else if ("isEnabledFor".equals(methodName)) {
                final PsiExpressionList argumentList =
                        methodCallExpression.getArgumentList();
                final PsiExpression[] arguments = argumentList.getExpressions();
                for (PsiExpression argument : arguments) {
                    if (!(argument instanceof PsiReferenceExpression)) {
                        continue;
                    }
                    final PsiReferenceExpression argumentReference =
                            (PsiReferenceExpression) argument;
                    final PsiType type = argument.getType();
                    if (!(type instanceof PsiClassType)) {
                        continue;
                    }
                    final PsiClassType classType = (PsiClassType) type;
                    final PsiClass aClass = classType.resolve();
                    if (aClass == null) {
                        continue;
                    }
                    final String qName = aClass.getQualifiedName();
                    if (!"org.apache.log4j.Priority".equals(qName)) {
                        continue;
                    }
                    final PsiElement argumentTarget =
                            argumentReference.resolve();
                    if (!(argumentTarget instanceof PsiField)) {
                        continue;
                    }
                    final PsiField field = (PsiField) argumentTarget;
                    final String name = field.getName();
                    enabledFor = Priority.valueOf(name);
                }
            } else {
                return;
            }
        }
    }


    /*Logger LOG = Logger.getLogger(
            LoggingConditionDisagreesWithLogStatementInspection.class);

    public void method() {
        // logging condition does not match log statement
        if (LOG.isDebugEnabled() || LOG.isInfoEnabled() || LOG.isEnabledFor(
                org.apache.log4j.Priority.ERROR)) {
            LOG.warn("asdfasdf");
            LOG.log()
                    LOG.debug();
            // warn, log fatal error info
        }
    }*/
}
