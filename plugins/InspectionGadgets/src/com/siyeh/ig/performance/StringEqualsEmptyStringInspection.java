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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class StringEqualsEmptyStringInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.equals.empty.string.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.equals.empty.string.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new StringEqualsEmptyStringFix();
    }

    private static class StringEqualsEmptyStringFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "string.equals.empty.string.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiIdentifier name =
                    (PsiIdentifier) descriptor.getPsiElement();
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) name.getParent();
            if (expression == null) {
                return;
            }
            final PsiExpression call = (PsiExpression)expression.getParent();
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String qualifierText = getRemainingText(qualifier);
            if (call == null) {
                return;
            }
            final PsiElement parent = call.getParent();
            if (parent instanceof PsiExpression) {
                final PsiExpression parentExpression = (PsiExpression) parent;
                if(BoolUtils.isNegation(parentExpression)) {
                    replaceExpression(parentExpression,
                            qualifierText + ".length()!=0");
                } else {
                    replaceExpression(call, qualifierText + ".length()==0");
                }
            } else {
                replaceExpression(call, qualifierText + ".length()==0");
            }
        }

        private static String getRemainingText(PsiExpression qualifier) {
            final String qualifierText;
            if (qualifier instanceof PsiMethodCallExpression) {
                // to replace stringBuffer.toString().equals("") with
                // stringBuffer.length() == 0
                final PsiMethodCallExpression callExpression =
                        (PsiMethodCallExpression) qualifier;
                final PsiReferenceExpression methodExpression =
                        callExpression.getMethodExpression();
                final String referenceName =
                        methodExpression.getReferenceName();
                final PsiExpression qualifierExpression =
                        methodExpression.getQualifierExpression();
                if (qualifierExpression == null) {
                    return qualifier.getText();
                }
                final PsiType type = qualifierExpression.getType();
                if (HardcodedMethodConstants.TO_STRING.equals(referenceName) &&
                        type != null &&
                        (type.equalsToText("java.lang.StringBuffer") ||
                                type.equalsToText("java.lang.StringBuilder"))) {
                    qualifierText = qualifierExpression.getText();
                } else {
                    qualifierText = qualifier.getText();
                }
            } else {
                qualifierText = qualifier.getText();
            }
            return qualifierText;
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringEqualsEmptyStringVisitor();
    }

    private static class StringEqualsEmptyStringVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"equals".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1) {
                return;
            }
            if (!ExpressionUtils.isEmptyStringLiteral(args[0])) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final PsiType type = qualifier.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final PsiElement context = call.getParent();
            if (context instanceof PsiExpressionStatement) {
                //cheesy, but necessary, because otherwise the quickfix will
                // produce uncompilable code (out of merely incorrect code).
                return;
            }
            registerMethodCallError(call);
        }
    }
}