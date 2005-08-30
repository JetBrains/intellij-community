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
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class IntegerDivisionInFloatingPointContextInspection extends ExpressionInspection {
    /** @noinspection StaticCollection*/
    private static final Set<String> s_integralTypes = new HashSet<String>(10);

    static {
      initIntegralTypes();
    }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initIntegralTypes() {
    s_integralTypes.add("int");
    s_integralTypes.add("long");
    s_integralTypes.add("short");
    s_integralTypes.add("byte");
    s_integralTypes.add("char");
    s_integralTypes.add("java.lang.Integer");
    s_integralTypes.add("java.lang.Long");
    s_integralTypes.add("java.lang.Short");
    s_integralTypes.add("java.lang.Byte");
    s_integralTypes.add("java.lang.Character");
  }

  public String getDisplayName() {
      return "Integer division in floating point context";
  }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref: integer division in floating-point context #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FloatingPointEqualityComparisonVisitor();
    }

    private static class FloatingPointEqualityComparisonVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.DIV)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiType lhsType = lhs.getType();
            if (!isIntegral(lhsType)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if(rhs== null)
            {
                return;
            }
            final PsiType rhsType = rhs.getType();
            if (!isIntegral(rhsType)) {
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
            if (!(contextType.equals(PsiType.FLOAT)
                    || contextType.equals(PsiType.DOUBLE))) {
                return;
            }
            registerError(expression);
        }


        private PsiExpression getContainingExpression(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (parent == null) {
                return expression;
            }
            if (parent instanceof PsiPrefixExpression ||
                    parent instanceof PsiPostfixExpression ||
                    parent instanceof PsiBinaryExpression ||
                    parent instanceof PsiParenthesizedExpression) {
                return getContainingExpression((PsiExpression) parent);
            }
            return expression;
        }
    }

    private static boolean isIntegral(PsiType type) {

        if (type == null) {
            return false;
        }
        final String text = type.getCanonicalText();
        if (text == null) {
            return false;
        }
        return s_integralTypes.contains(text);
    }

}
