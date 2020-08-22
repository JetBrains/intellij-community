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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class IntegerMultiplicationImplicitCastToLongInspection extends BaseInspection {
  private static final CallMatcher JUNIT4_ASSERT_EQUALS =
    CallMatcher.anyOf(
      CallMatcher.staticCall("org.junit.Assert", "assertEquals").parameterTypes("long", "long"),
      CallMatcher.staticCall("org.junit.Assert", "assertEquals").parameterTypes(CommonClassNames.JAVA_LANG_STRING, "long", "long")
    );

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

  @SuppressWarnings("PublicField")
  public boolean ignoreNonOverflowingCompileTimeConstants = true;

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntegerMultiplicationImplicitCastToLongInspectionFix();
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

  private static boolean isNonLongInteger(PsiType type) {
    if (type == null) return false;
    final String text = type.getCanonicalText();
    return s_typesToCheck.contains(text);
  }

  /**
   * Checks whether one of operands of polyadic expression itself is polyadic expression with multiplication operator.
   * For shift operations only first operand is considered.
   */
  private static boolean hasInnerMultiplication(@NotNull PsiPolyadicExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    if (isShiftToken(tokenType)) {
      return hasMultiplication(expression.getOperands()[0]);
    }

    return Arrays.stream(expression.getOperands()).anyMatch(operand -> hasMultiplication(operand));
  }

  private static boolean hasMultiplication(PsiExpression expression) {
    expression = PsiUtil.deparenthesizeExpression(expression);

    if (expression instanceof PsiPrefixExpression) {
      return hasMultiplication(((PsiPrefixExpression)expression).getOperand());
    }

    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyExpr = (PsiPolyadicExpression)expression;
      final IElementType tokenType = polyExpr.getOperationTokenType();

      if (tokenType == JavaTokenType.ASTERISK) {
        return true;
      }

      return hasInnerMultiplication(polyExpr);
    }

    if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression ternary = (PsiConditionalExpression)expression;
      return hasMultiplication(ternary.getThenExpression()) || hasMultiplication(ternary.getElseExpression());
    }

    return false;
  }

  private static boolean isShiftToken(IElementType tokenType) {
    return tokenType.equals(JavaTokenType.LTLT) ||
           tokenType.equals(JavaTokenType.GTGT) ||
           tokenType.equals(JavaTokenType.GTGTGT);
  }

  private static class IntegerMultiplicationImplicitCastToLongInspectionFix extends InspectionGadgetsFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("integer.multiplication.implicit.cast.to.long.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiPolyadicExpression expression = (PsiPolyadicExpression)descriptor.getPsiElement();

      final PsiExpression[] operands = expression.getOperands();
      if (operands.length < 2) {
        return;
      }

      final PsiExpression exprToCast;
      if (operands.length > 2 || expression.getOperationTokenType() == JavaTokenType.LTLT) {
        exprToCast = operands[0];
      }
      else {
        exprToCast = Arrays.stream(operands)
          .map(operand -> PsiUtil.deparenthesizeExpression(operand))
          .filter(operand -> operand instanceof PsiLiteralExpression ||
                             operand instanceof PsiPrefixExpression && ((PsiPrefixExpression)operand).getOperand() instanceof PsiLiteral)
          .findFirst()
          .orElse(operands[0]);
      }

      addCast(exprToCast);
    }

    private static void addCast(@NotNull PsiExpression expression) {
      if (expression instanceof PsiPrefixExpression) {
        final PsiExpression operand = ((PsiPrefixExpression)expression).getOperand();
        if (operand instanceof PsiLiteralExpression) expression = operand;
      }

      final String replacementText;
      if (expression instanceof PsiLiteralExpression) {
        replacementText = expression.getText() + "L";
      }
      else {
        replacementText = "(long)" + expression.getText();
      }

      PsiReplacementUtil.replaceExpression(expression, replacementText);
    }
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
      if (hasInnerMultiplication(expression)) {
        return;
      }
      if (insideAssertEquals(expression)) {
        return;
      }
      PsiExpression[] operands = expression.getOperands();
      if (operands.length < 2 || expression.getLastChild() instanceof PsiErrorElement) {
        return;
      }
      PsiExpression context = getContainingExpression(expression);
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(context.getParent());
      if (parent instanceof PsiTypeCastExpression) {
        PsiType castType = ((PsiTypeCastExpression)parent).getType();
        if (isNonLongInteger(castType)) return;
        if (PsiType.LONG.equals(castType)) context = (PsiExpression)parent;
      }
      if (!PsiType.LONG.equals(context.getType()) &&
          !PsiType.LONG.equals(ExpectedTypeUtils.findExpectedType(context, true))) {
        return;
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

    private boolean insideAssertEquals(PsiExpression expression) {
      PsiElement parent = ExpressionUtils.getPassThroughParent(expression);
      if (parent instanceof PsiExpressionList) {
        PsiMethodCallExpression call = ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
        // JUnit4 has assertEquals(long, long) but no assertEquals(int, int)
        // so int-assertions will be wired to assertEquals(long, long), which could be annoying false-positive.
        // If the multiplication unexpectedly overflows then assertion will fail anyway, so the problem will manifest itself.
        return JUNIT4_ASSERT_EQUALS.matches(call);
      }
      return false;
    }

    private boolean cannotOverflow(@NotNull PsiPolyadicExpression expression, PsiExpression[] operands, boolean shift) {
      CommonDataflow.DataflowResult dfr = CommonDataflow.getDataflowResult(expression);
      if (dfr != null) {
        long min = 1, max = 1;
        for (PsiExpression operand : operands) {
          LongRangeSet set = DfLongType.extractRange(dfr.getDfType(PsiUtil.skipParenthesizedExprDown(operand)));
          if (operand == operands[0]) {
            min = set.min();
            max = set.max();
            continue;
          }
          long r1, r2, r3, r4;
          if (shift) {
            set = set.bitwiseAnd(LongRangeSet.point(0x3F));
            long nextMin = set.min();
            long nextMax = set.max();
            if (nextMax >= 0x20) return false;
            r1 = min << nextMin;
            r2 = max << nextMin;
            r3 = min << nextMax;
            r4 = max << nextMax;
          } else {
            long nextMin = set.min();
            long nextMax = set.max();
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
        final PsiPolyadicExpression polyParent = (PsiPolyadicExpression)parent;
        final IElementType tokenType = polyParent.getOperationTokenType();
        if (!isShiftToken(tokenType) || expression == polyParent.getOperands()[0]) {
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
  }
}