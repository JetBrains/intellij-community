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
package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class IntegerMultiplicationImplicitCastToLongInspection extends ExpressionInspection {
    /** @noinspection StaticCollection*/
    private static final Set<String> s_typesToCheck = new HashSet<String>(10);

    static {
        s_typesToCheck.add("int");
        s_typesToCheck.add("short");
        s_typesToCheck.add("byte");
        s_typesToCheck.add("char");
    }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "integer.multiplication.implicit.cast.to.long.problem.descriptor");
    }
    public BaseInspectionVisitor buildVisitor() {
        return new IntegerMultiplicationImplicitlyCastToLongVisitor();
    }

    private static class IntegerMultiplicationImplicitlyCastToLongVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.ASTERISK)
                    && !tokenType.equals(JavaTokenType.LTLT)) {
                return;
            }
            final PsiType type = expression.getType();
            if (!isNonLongInteger(type)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if(rhs== null)
            {
                return;
            }
            final PsiType rhsType = rhs.getType();
            if (!isNonLongInteger(rhsType)) {
                return;
            }
            final PsiExpression context = getContainingExpression(expression);
            if (context == null) {
                return;
            }
            final PsiType contextType = ExpectedTypeUtils.findExpectedType(context, true);
            if (contextType == null) {
                return;
            }
            if (!contextType.equals(PsiType.LONG)) {
                return;
            }
            registerError(expression);
        }


        private static PsiExpression getContainingExpression(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (parent == null) {
                return expression;
            }
            if ( parent instanceof PsiConditionalExpression ||
                    parent instanceof PsiParenthesizedExpression) {
                return getContainingExpression((PsiExpression) parent);
            }
            return expression;
        }
    }

    private static boolean isNonLongInteger(PsiType type) {

        if (type == null) {
            return false;
        }
        final String text = type.getCanonicalText();
        if (text == null) {
            return false;
        }
        return s_typesToCheck.contains(text);
    }

}
