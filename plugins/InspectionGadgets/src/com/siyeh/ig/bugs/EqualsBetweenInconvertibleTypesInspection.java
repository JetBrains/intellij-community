/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

public class EqualsBetweenInconvertibleTypesInspection
        extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "equals.between.inconvertible.types.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiReferenceExpression methodExpression =
                (PsiReferenceExpression) location.getParent();
        assert methodExpression != null;
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) methodExpression.getParent();
        assert expression != null;
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiType comparedType = args[0].getType();
        assert comparedType != null;
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        assert qualifier != null;
        final PsiType comparisonType = qualifier.getType();
        assert comparisonType != null;
        return InspectionGadgetsBundle.message(
                "equals.between.inconvertible.types.problem.descriptor",
                comparisonType.getPresentableText(),
                                               comparedType.getPresentableText());
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EqualsBetweenInconvertibleTypesVisitor();
    }

    private static class EqualsBetweenInconvertibleTypesVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if(!MethodCallUtils.isEqualsCall(expression)){
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1) {
                return;
            }
            final PsiExpression arg = args[0];
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!areIncompatibleTypes(arg, qualifier)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean areIncompatibleTypes(PsiExpression expression1,
                                                    PsiExpression expression2) {
            if (expression1 == null) {
                return false;
            }
            final PsiType comparedType = expression1.getType();
            if (comparedType == null) {
                return false;
            }
            if (expression2 == null) {
                return false;
            }
            final PsiType comparisonType = expression2.getType();
            if (comparisonType == null) {
                return false;
            }
            return !TypeConversionUtil.areTypesConvertible(comparedType,
                                                           comparisonType);
        }
    }

}
