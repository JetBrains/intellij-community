/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.VariableSearchUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryThisInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("unnecessary.this.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.this.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryThisFix();
    }

    private static class UnnecessaryThisFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.this.remove.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement thisToken = descriptor.getPsiElement();
            final PsiReferenceExpression thisExpression =
                    (PsiReferenceExpression)thisToken.getParent();
            assert thisExpression != null;
            final String newExpression = thisExpression.getReferenceName();
            if (newExpression == null) {
                return;
            }
            replaceExpression(thisExpression, newExpression);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryThisVisitor();
    }

    private static class UnnecessaryThisVisitor extends BaseInspectionVisitor {

        @Override public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiReferenceParameterList parameterList =
                    expression.getParameterList();
            if (parameterList == null) {
                return;
            }
            if (parameterList.getTypeArguments().length > 0) {
                return;
            }
            final PsiExpression qualifierExpression =
                    expression.getQualifierExpression();
            if (!(qualifierExpression instanceof PsiThisExpression)) {
                return;
            }
            final PsiThisExpression thisExpression =
                    (PsiThisExpression)qualifierExpression;
            final PsiJavaCodeReferenceElement qualifier =
                    thisExpression.getQualifier();
            final String referenceName = expression.getReferenceName();
            if (referenceName == null) {
                return;
            }
            final PsiElement parent = expression.getParent();
            if (qualifier == null) {
                if (parent instanceof PsiCallExpression) {
                    // method calls are always in error
                    registerError(qualifierExpression);
                    return;
                }
                if (VariableSearchUtils.existsLocalOrParameter(referenceName,
                        expression)) {
                    return;
                }
                registerError(thisExpression);
            } else {
                final String qualifierName = qualifier.getReferenceName();
                if (qualifierName == null) {
                    return;
                }
                if (parent instanceof PsiCallExpression) {
                    final PsiCallExpression callExpression =
                            (PsiCallExpression) parent;
                    final PsiMethod calledMethod =
                            callExpression.resolveMethod();
                    if (calledMethod == null) {
                        return;
                    }
                    final String methodName = calledMethod.getName();
                    PsiClass parentClass =
                            ClassUtils.getContainingClass(expression);
                    while (parentClass != null) {
                        if (qualifierName.equals(parentClass.getName())) {
                            registerError(thisExpression);
                        }
                        //resolve will point to any _accessible_ method with the same name
                        final PsiMethod[] methods =
                                parentClass.findMethodsByName(methodName,
                                        true);
                        //todo: filter only accessible methods
                        if (methods.length > 0) {
                            return;
                        }
                        parentClass =
                                ClassUtils.getContainingClass(parentClass);
                    }
                } else {
                    if (VariableSearchUtils.existsLocalOrParameter(referenceName,
                            expression)) {
                        return;
                    }
                    PsiClass parentClass =
                            ClassUtils.getContainingClass(expression);
                    while (parentClass != null) {
                        if (qualifierName.equals(parentClass.getName())) {
                            registerError(thisExpression);
                        }
                        final PsiField field =
                                parentClass.findFieldByName(referenceName, true);
                        if (field != null) {
                            return;
                        }
                        parentClass =
                                ClassUtils.getContainingClass(parentClass);
                    }
                }
            }
        }
    }
}