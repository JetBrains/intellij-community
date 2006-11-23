/*
 * Copyright 2006 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ListIndexOfReplaceableByContainsInspection
        extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "indexof.replaceable.by.contains.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiBinaryExpression expression = (PsiBinaryExpression)infos[0];
        final PsiExpression lhs = expression.getLOperand();
        final PsiJavaToken sign = expression.getOperationSign();
        final String text;
        if (lhs instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression)lhs;
            text = createContainsExpressionText(callExpression, sign, false);
        } else {
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression)expression.getROperand();
            assert callExpression != null;
            text = createContainsExpressionText(callExpression, sign, true);
        }
        return InspectionGadgetsBundle.message(
                "indexof.replaceable.by.contains.problem.descriptor", text);
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new IndexOfReplaceableByContainsFix();
    }

    private static class IndexOfReplaceableByContainsFix
            extends InspectionGadgetsFix {

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiBinaryExpression expression =
                    (PsiBinaryExpression)descriptor.getPsiElement();
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            final PsiJavaToken sign = expression.getOperationSign();
            final String newExpressionText;
            if (lhs instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression callExpression =
                        (PsiMethodCallExpression)lhs;
                newExpressionText =
                        createContainsExpressionText(callExpression, sign,
                                false);
            } else {
                final PsiMethodCallExpression callExpression =
                        (PsiMethodCallExpression)rhs;
                assert callExpression != null;
                newExpressionText =
                        createContainsExpressionText(callExpression, sign, true);
            }
            replaceExpression(expression, newExpressionText);
        }

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "replace.indexof.with.contains.quickfix");
        }
    }

    static String createContainsExpressionText(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiJavaToken sign,
            boolean flipped) {
        final IElementType tokenType = sign.getTokenType();
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression qualifierExpression =
                methodExpression.getQualifierExpression();
        final String qualifierText;
        if (qualifierExpression == null) {
            qualifierText = "";
        } else {
            qualifierText = qualifierExpression.getText();
        }
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression expression = argumentList.getExpressions()[0];
        @NonNls final String newExpressionText =
                qualifierText + ".contains(" + expression.getText() + ')';
        if (tokenType.equals(JavaTokenType.EQEQ)) {
            return '!' + newExpressionText;
        } else if (!flipped && (tokenType.equals(JavaTokenType.LT) ||
                                tokenType.equals(JavaTokenType.LE))) {
            return '!' + newExpressionText;
        } else if (flipped && (tokenType.equals(JavaTokenType.GT) ||
                               tokenType.equals(JavaTokenType.GE))) {
            return '!' + newExpressionText;
        }
        return newExpressionText;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new IndexOfReplaceableByContainsVisitor();
    }

    private static class IndexOfReplaceableByContainsVisitor
            extends BaseInspectionVisitor {

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            if (!ComparisonUtils.isComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (lhs instanceof PsiMethodCallExpression) {
                final PsiJavaToken sign = expression.getOperationSign();
                if (canBeReplacedByContains(lhs, sign, rhs, false)) {
                    registerError(expression, expression);
                }
            } else if (rhs instanceof PsiMethodCallExpression) {
                final PsiJavaToken sign = expression.getOperationSign();
                if (canBeReplacedByContains(rhs, sign, lhs, true)) {
                    registerError(expression, expression);
                }
            }
        }

        private static boolean canBeReplacedByContains(
                PsiExpression lhs, PsiJavaToken sign,
                PsiExpression rhs, boolean flipped) {
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression)lhs;
            if (!isIndexOfCall(callExpression)) {
                return false;
            }
            final PsiManager manager = lhs.getManager();
            final PsiConstantEvaluationHelper constantEvaluationHelper =
                    manager.getConstantEvaluationHelper();
            final Object object =
                    constantEvaluationHelper.computeConstantExpression(rhs);
            if (!(object instanceof Integer)) {
                return false;
            }
            final Integer integer = (Integer)object;
            final int constant = integer.intValue();
            final IElementType tokenType = sign.getTokenType();
            if (flipped) {
                if (constant == -1 && (JavaTokenType.NE.equals(tokenType) ||
                                       JavaTokenType.LT.equals(tokenType) ||
                                       JavaTokenType.EQEQ.equals(tokenType) ||
                                       JavaTokenType.GE.equals(tokenType))) {
                    return true;
                } else if (constant == 0 &&
                           (JavaTokenType.LE.equals(tokenType) ||
                            JavaTokenType.GT.equals(tokenType))) {
                    return true;
                }
            } else {
                if (constant == -1 && (JavaTokenType.NE.equals(tokenType) ||
                                       JavaTokenType.GT.equals(tokenType) ||
                                       JavaTokenType.EQEQ.equals(tokenType) ||
                                       JavaTokenType.LE.equals(tokenType))) {
                    return true;
                } else if (constant == 0 &&
                           (JavaTokenType.GE.equals(tokenType) ||
                            JavaTokenType.LT.equals(tokenType))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isIndexOfCall(
                @NotNull PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.INDEX_OF.equals(methodName)) {
                return false;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            final PsiType qualifierType = qualifier.getType();
            if (qualifierType == null) {
                return false;
            }
            final Project project = expression.getProject();
            final PsiManager manager = expression.getManager();
            final GlobalSearchScope projectScope =
                    GlobalSearchScope.allScope(project);
            final PsiClass javaUtilListClass =
                    manager.findClass("java.util.List", projectScope);
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiClassType javaUtilListType =
                    factory.createType(javaUtilListClass);
            return javaUtilListType.isAssignableFrom(qualifierType);
        }
    }
}