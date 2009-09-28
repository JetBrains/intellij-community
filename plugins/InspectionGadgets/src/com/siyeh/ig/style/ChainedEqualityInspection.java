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

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ChainedEqualityInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "ChainedEqualityComparisons";
    }
    
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "chained.equality.comparisons.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "chained.equality.comparisons.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ChainedEqualityVisitor();
    }

    private static class ChainedEqualityVisitor extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)) {
                return;
            }
            if (!isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (!(lhs instanceof PsiBinaryExpression)) {
                return;
            }
            if (!isEqualityComparison((PsiBinaryExpression) lhs)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isEqualityComparison(
                @NotNull PsiBinaryExpression expression) {
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return tokenType.equals(JavaTokenType.EQEQ) ||
                    tokenType.equals(JavaTokenType.NE);
        }
    }
}