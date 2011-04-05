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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ObjectToStringInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
      return InspectionGadgetsBundle.message(
              "default.tostring.call.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "default.tostring.call.problem.descriptor");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ObjectToStringVisitor();
    }

    private static class ObjectToStringVisitor
            extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiType type = expression.getType();
            if(type == null) {
                return;
            }
            if(!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            checkExpression(lhs);
            final PsiExpression rhs = expression.getROperand();
            checkExpression(rhs);
        }

        @Override public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSEQ)) {
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            final PsiType type = lhs.getType();
            if (type == null) {
                return;
            }
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final PsiExpression rhs = expression.getRExpression();
            checkExpression(rhs);
        }

        @Override public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String name = methodExpression.getReferenceName();
            if (HardcodedMethodConstants.TO_STRING.equals(name)) {
                final PsiExpressionList argumentList =
                        expression.getArgumentList();
                final PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length != 0) {
                    return;
                }
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                checkExpression(qualifier);
            } else if ("append".equals(name)) {
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (!TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
                    return;
                }
                final PsiExpressionList argumentList =
                        expression.getArgumentList();
                final PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length != 1) {
                    return;
                }
                final PsiExpression argument = arguments[0];
                checkExpression(argument);
            } else if ("valueOf".equals(name)) {
                final PsiExpression qualifierExpression =
                        methodExpression.getQualifierExpression();
                if (!(qualifierExpression instanceof PsiReferenceExpression)) {
                    return;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) qualifierExpression;
                final String canonicalText =
                        referenceExpression.getCanonicalText();
                if (!CommonClassNames.JAVA_LANG_STRING.equals(canonicalText)) {
                    return;
                }
                final PsiExpressionList argumentList =
                        expression.getArgumentList();
                final PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length != 1) {
                    return;
                }
                final PsiExpression argument = arguments[0];
                final PsiType type = argument.getType();
                if (type instanceof PsiArrayType) {
                    final PsiType componentType =
                            ((PsiArrayType) type).getComponentType();
                    if (PsiType.CHAR.equals(componentType)) {
                        return;
                    }
                }
                checkExpression(argument);
            }
        }

        private void checkExpression(PsiExpression expression) {
            if(expression == null) {
                return;
            }
            final PsiType type = expression.getType();
            if(type == null) {
                return;
            }
            if(type instanceof PsiArrayType) {
                registerError(expression);
                return;
            }
            if(!(type instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) type;
            if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
                return;
            }
            final PsiClass referencedClass = classType.resolve();
            if(referencedClass == null ||
               referencedClass instanceof PsiTypeParameter) {
                return;
            }
            if(referencedClass.isEnum() || referencedClass.isInterface()) {
                return;
            }
            if (hasGoodToString(referencedClass, new HashSet<PsiClass>())) {
                return;
            }
            registerError(expression);
        }

        private static boolean hasGoodToString(PsiClass aClass,
                                               Set<PsiClass> visitedClasses) {
            if(aClass == null) {
                return false;
            }
            if (!visitedClasses.add(aClass)) {
                return true;
            }
            final String className = aClass.getQualifiedName();
            if("java.lang.Object".equals(className)) {
                return false;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(PsiMethod method : methods) {
                if(isToString(method)) {
                    return true;
                }
            }
            final PsiClass superClass = aClass.getSuperClass();
            return hasGoodToString(superClass, visitedClasses);
        }

        private static boolean isToString(PsiMethod method) {
            final String methodName = method.getName();
            if(!HardcodedMethodConstants.TO_STRING.equals(methodName)){
                return false;
            }
            final PsiParameterList parameterList = method.getParameterList();
            return parameterList.getParametersCount() == 0;
        }
    }
}