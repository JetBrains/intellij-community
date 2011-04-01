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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class EqualsWhichDoesntCheckParameterClassInspection
        extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "equals.doesnt.check.class.parameter.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "equals.doesnt.check.class.parameter.problem.descriptor");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new EqualsWhichDoesntCheckParameterClassVisitor();
    }

    private static class EqualsWhichDoesntCheckParameterClassVisitor
            extends BaseInspectionVisitor{

        @Override public void visitMethod(@NotNull PsiMethod method){
              // note: no call to super
            if(!MethodUtils.isEquals(method)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiParameter parameter = parameters[0];
            final PsiCodeBlock body = method.getBody();
            if(body == null){
                return;
            }
            if(isParameterChecked(body, parameter)){
                return;
            }
            if (isParameterCheckNotNeeded(body, parameter)) {
                return;
            }
            registerMethodError(method);
        }

        private static boolean isParameterCheckNotNeeded(
                PsiCodeBlock body, PsiParameter parameter) {
            final PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                return true;
            }
            if (statements.length != 1) {
                return false;
            }
            final PsiStatement statement = statements[0];
            if (!(statement instanceof PsiReturnStatement)) {
                return false;
            }
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement) statement;
            final PsiExpression returnValue =
                    returnStatement.getReturnValue();
            final Object constant =
                    ExpressionUtils.computeConstantExpression(returnValue);
            if (Boolean.FALSE.equals(constant)) {
                return true;
            }
            if (!(returnValue instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) returnValue;
            return isCallToSuperEquals(methodCallExpression, parameter);
        }

        private static boolean isCallToSuperEquals(
                PsiMethodCallExpression methodCallExpression,
                PsiParameter parameter) {
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (!(qualifierExpression instanceof PsiSuperExpression)) {
                return false;
            }
            final String name = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.EQUALS.equals(name)) {
                return false;
            }
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return false;
            }
            final PsiExpression argument = arguments[0];
            if (!(argument instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) argument;
            final PsiElement target = referenceExpression.resolve();
            return parameter.equals(target);
        }

        private static boolean isParameterChecked(PsiCodeBlock body,
                                                  PsiParameter parameter){
            if (usesEqualsBuilderReflectionEquals(body)) {
                return true;
            }
            final ParameterClassCheckVisitor visitor =
                    new ParameterClassCheckVisitor(parameter);
            body.accept(visitor);
            return visitor.isChecked();
        }

        private static boolean usesEqualsBuilderReflectionEquals(
                PsiCodeBlock body) {
            final PsiStatement[] statements = body.getStatements();
            if (statements.length != 1) {
                return false;
            }
            final PsiStatement statement = statements[0];
            if (!(statement instanceof PsiReturnStatement)) {
                return false;
            }
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement) statement;
            final PsiExpression returnValue =
                    returnStatement.getReturnValue();
            if (!(returnValue instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) returnValue;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            @NonNls final String referenceName =
                    methodExpression.getReferenceName();
            if (!"reflectionEquals".equals(referenceName)) {
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) qualifier;
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiClass)) {
                return false;
            }
            final PsiClass aClass = (PsiClass) target;
            final String className = aClass.getQualifiedName();
            return "org.apache.commons.lang.builder.EqualsBuilder".equals(
                    className);
        }
    }
}