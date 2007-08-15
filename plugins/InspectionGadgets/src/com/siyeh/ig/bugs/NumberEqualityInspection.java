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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EqualityToEqualsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class NumberEqualityInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "number.comparison.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "number.comparison.problem.descriptor");
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectEqualityVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new EqualityToEqualsFix();
    }

    private static class ObjectEqualityVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            if (!ComparisonUtils.isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (!hasNumberType(lhs)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (!hasNumberType(rhs)) {
                return;
            }
            final String lhsText = lhs.getText();
            if (PsiKeyword.NULL.equals(lhsText)) {
                return;
            }
            if (rhs == null) {
                return;
            }
            final String rhsText = rhs.getText();
            if (PsiKeyword.NULL.equals(rhsText)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            registerError(sign);
        }

        private static boolean hasNumberType(PsiExpression lhs) {
            return TypeUtils.expressionHasTypeOrSubtype(lhs, "java.lang.Number");
        }
    }
}