/*
 * Copyright 2006-2014 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class IntegerMultiplicationImplicitCastToLongInspection extends BaseInspection {

  /**
   */
  @NonNls
  private static final Set<String> s_typesToCheck = new HashSet<>(4);

  static {
    s_typesToCheck.add("int");
    s_typesToCheck.add("short");
    s_typesToCheck.add("byte");
    s_typesToCheck.add("char");
    s_typesToCheck.add(CommonClassNames.JAVA_LANG_INTEGER);
    s_typesToCheck.add(CommonClassNames.JAVA_LANG_SHORT);
    s_typesToCheck.add(CommonClassNames.JAVA_LANG_BYTE);
    s_typesToCheck.add(CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @SuppressWarnings({"PublicField"})
  public boolean ignoreNonOverflowingCompileTimeConstants = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "integer.multiplication.implicit.cast.to.long.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final IElementType tokenType = (IElementType)infos[0];
    if (JavaTokenType.ASTERISK.equals(tokenType)) {
      return InspectionGadgetsBundle.message("integer.multiplication.implicit.cast.to.long.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("integer.shift.implicit.cast.to.long.problem.descriptor");
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "integer.multiplication.implicit.cast.to.long.option"),
                                          this, "ignoreNonOverflowingCompileTimeConstants");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IntegerMultiplicationImplicitlyCastToLongVisitor();
  }

  private class IntegerMultiplicationImplicitlyCastToLongVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ASTERISK) && !tokenType.equals(JavaTokenType.LTLT)) {
        return;
      }
      final PsiType type = expression.getType();
      if (!isNonLongInteger(type)) {
        return;
      }
      PsiExpression[] operands = expression.getOperands();
      if (operands.length < 2 || expression.getLastChild() instanceof PsiErrorElement) {
        return;
      }
      final PsiExpression context = getContainingExpression(expression);
      if (context == null) return;
      if (!PsiType.LONG.equals(context.getType()) && 
          !PsiType.LONG.equals(ExpectedTypeUtils.findExpectedType(context, true))) {
        return;
      }
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(context.getParent());
      if (parent instanceof PsiTypeCastExpression) {
        PsiType castType = ((PsiTypeCastExpression)parent).getType();
        if (isNonLongInteger(castType)) return;
      }
      if (ignoreNonOverflowingCompileTimeConstants) {
        try {
          if (ExpressionUtils.computeConstantExpression(expression, true) != null) {
            return;
          }
        }
        catch (ConstantEvaluationOverflowException ignore) {
        }
        if (cannotOverflow(expression, operands, tokenType.equals(JavaTokenType.LTLT))) {
          return;
        }
      }
      registerError(expression, tokenType);
    }

    private boolean cannotOverflow(@NotNull PsiPolyadicExpression expression, PsiExpression[] operands, boolean shift) {
      CommonDataflow.DataflowResult dfr = CommonDataflow.getDataflowResult(expression);
      if (dfr != null) {
        long min = 1, max = 1;
        for (PsiExpression operand : operands) {
          LongRangeSet set = dfr.getExpressionFact(PsiUtil.skipParenthesizedExprDown(operand), DfaFactType.RANGE);
          if (set == null) return false;
          long nextMin = set.min();
          long nextMax = set.max();
          if (operand == operands[0]) {
            min = nextMin;
            max = nextMax;
            continue;
          }
          long r1, r2, r3, r4;
          if (shift) {
            nextMin &= 0x1F;
            nextMax &= 0x1F;
            r1 = min << nextMin;
            r2 = max << nextMin;
            r3 = min << nextMax;
            r4 = max << nextMax;
          } else {
            if (intOverflow(nextMin) || intOverflow(nextMax)) return false;
            r1 = min * nextMin;
            r2 = max * nextMin;
            r3 = min * nextMax;
            r4 = max * nextMax;
          }
          if (intOverflow(r1) || intOverflow(r2) || intOverflow(r3) || intOverflow(r4)) return false;
          min = Math.min(Math.min(r1, r2), Math.min(r3, r4));
          max = Math.max(Math.max(r1, r2), Math.max(r3, r4));
        }
      }
      return true;
    }

    private boolean intOverflow(long l) {
      return (int)l != l && l != Integer.MAX_VALUE + 1L;
    }

    private PsiExpression getContainingExpression(
      PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiPolyadicExpression && TypeConversionUtil.isNumericType(((PsiPolyadicExpression)parent).getType())) {
        IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.LTLT) && !tokenType.equals(JavaTokenType.GTGT) && !tokenType.equals(JavaTokenType.GTGTGT)) {
          return getContainingExpression((PsiExpression)parent);
        }
      }
      if (parent instanceof PsiParenthesizedExpression ||
          parent instanceof PsiPrefixExpression ||
          parent instanceof PsiConditionalExpression) {
        return getContainingExpression((PsiExpression)parent);
      }
      return expression;
    }

    private boolean isNonLongInteger(PsiType type) {
      if (type == null) return false;
      final String text = type.getCanonicalText();
      return s_typesToCheck.contains(text);
    }
  }
}