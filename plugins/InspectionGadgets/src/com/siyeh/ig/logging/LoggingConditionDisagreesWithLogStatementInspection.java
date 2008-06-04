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

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class LoggingConditionDisagreesWithLogStatementInspection
        extends BaseInspection {

    enum Log4jPriority {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }
    enum UtilLoggingLevel {
        SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST
    }

    private static final Map<String, Log4jPriority> log4jLogMethodNames =
            new HashMap();
    static {
        log4jLogMethodNames.put("debug", Log4jPriority.DEBUG);
        log4jLogMethodNames.put("error", Log4jPriority.ERROR);
        log4jLogMethodNames.put("fatal", Log4jPriority.FATAL);
        log4jLogMethodNames.put("info", Log4jPriority.INFO);
        log4jLogMethodNames.put("trace", Log4jPriority.TRACE);
        log4jLogMethodNames.put("warn", Log4jPriority.WARN);
    }

    private static final Map<String, UtilLoggingLevel> utilLogMethodNames =
            new HashMap();
    static {
        utilLogMethodNames.put("severe", UtilLoggingLevel.SEVERE);
        utilLogMethodNames.put("warning", UtilLoggingLevel.WARNING);
        utilLogMethodNames.put("info", UtilLoggingLevel.INFO);
        utilLogMethodNames.put("config", UtilLoggingLevel.CONFIG);
        utilLogMethodNames.put("fine", UtilLoggingLevel.FINE);
        utilLogMethodNames.put("finer", UtilLoggingLevel.FINER);
        utilLogMethodNames.put("finest", UtilLoggingLevel.FINEST);
    }



    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "logging.condition.disagrees.with.log.statement.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "logging.condition.disagrees.with.log.statement.problem.descriptor",
                infos[0]);
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
            Log4jPriority priority = log4jLogMethodNames.get(referenceName);
            UtilLoggingLevel level = utilLogMethodNames.get(referenceName);
            if (priority == null && level == null) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            final String qualifiedName = containingClass.getQualifiedName();
            boolean log4j = false;
            boolean javaUtilLogging = false;
            if ("org.apache.log4j.Logger".equals(qualifiedName) ||
                    "org.apache.log4j.Category".equals(qualifiedName)) {
                log4j = true;
            } else if ("java.util.logging.Logger".equals(qualifiedName)) {
                javaUtilLogging = true;
            } else {
                return;
            }
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiExpressionStatement)) {
                return;
            }
            final PsiElement grandParent = parent.getParent();
            final PsiIfStatement ifStatement;
            if (grandParent instanceof PsiCodeBlock) {
                final PsiElement greatGrandParent = grandParent.getParent();
                if (!(greatGrandParent instanceof PsiBlockStatement)) {
                    return;
                }
                final PsiElement greatGreatGrandParent =
                        greatGrandParent.getParent();
                if (!(greatGreatGrandParent instanceof PsiIfStatement)) {
                    return;
                }
                ifStatement = (PsiIfStatement) greatGreatGrandParent;
            } else if (grandParent instanceof PsiIfStatement) {
                ifStatement = (PsiIfStatement) grandParent;
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
            if (log4j) {
                Log4jPriority enabledFor = null;
                if ("isDebugEnabled".equals(methodName)) {
                    enabledFor = Log4jPriority.DEBUG;
                } else if ("isInfoEnabled".equals(methodName)) {
                    enabledFor = Log4jPriority.INFO;
                } else if ("isTraceEnabled".equals(methodName)) {
                    enabledFor = Log4jPriority.TRACE;
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
                        enabledFor = Log4jPriority.valueOf(name);
                    }
                } else {
                    return;
                }
                if (enabledFor == priority) {
                    return;
                }
            } else if (javaUtilLogging) {
                System.out.println("here");
                if (!"isLoggable".equals(methodName)) {
                    return;
                }
                final PsiExpressionList argumentList =
                        methodCallExpression.getArgumentList();
                final PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length != 1) {
                    return;
                }
                final PsiExpression argument = arguments[0];
                if (!(argument instanceof PsiReferenceExpression)) {
                    return;
                }
                final PsiReferenceExpression argumentReference =
                        (PsiReferenceExpression) argument;
                final PsiType type = argument.getType();
                if (!(type instanceof PsiClassType)) {
                    return;
                }
                final PsiClassType classType = (PsiClassType) type;
                final PsiClass aClass = classType.resolve();
                if (aClass == null) {
                    return;
                }
                final String qName = aClass.getQualifiedName();
                if (!"java.util.logging.Level".equals(qName)) {
                    return;
                }
                final PsiElement argumentTarget =
                        argumentReference.resolve();
                if (!(argumentTarget instanceof PsiField)) {
                    return;
                }
                final PsiField field = (PsiField) argumentTarget;
                final String name = field.getName();
                UtilLoggingLevel enabledFor =
                        UtilLoggingLevel.valueOf(name);
                if (enabledFor == level) {
                    return;
                }
            }
            registerMethodCallError(methodCallExpression, referenceName);
        }
    }
}
