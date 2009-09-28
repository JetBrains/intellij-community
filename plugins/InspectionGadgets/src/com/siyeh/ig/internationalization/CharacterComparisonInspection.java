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
package com.siyeh.ig.internationalization;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiKeyword;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class CharacterComparisonInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "CharacterComparison";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "character.comparison.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "character.comparison.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CharacterComparisonVisitor();
    }

    private static class CharacterComparisonVisitor
            extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiExpression rhs = expression.getROperand();
            if(!(rhs != null)){
                return;
            }
            if(!ComparisonUtils.isComparison(expression)){
                return;
            }
            if(ComparisonUtils.isEqualityComparison(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (!isCharacter(lhs)) {
                return;
            }
            if (!isCharacter(rhs)) {
                return;
            }
            if (NonNlsUtils.isNonNlsAnnotated(lhs) ||
                NonNlsUtils.isNonNlsAnnotated(rhs)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isCharacter(PsiExpression lhs) {
            return  TypeUtils.expressionHasType(PsiKeyword.CHAR, lhs) ||
                    TypeUtils.expressionHasType("java.lang.Character", lhs);
        }
    }
}