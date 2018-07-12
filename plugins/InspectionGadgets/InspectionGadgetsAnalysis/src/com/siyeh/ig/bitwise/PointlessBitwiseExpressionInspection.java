/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bitwise;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

import static com.intellij.psi.JavaTokenType.*;

public class PointlessBitwiseExpressionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreExpressionsContainingConstants = true;

  static final Set<IElementType> bitwiseTokens = ContainerUtil.immutableSet(AND, OR, XOR, LTLT, GTGT, GTGTGT);

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "pointless.bitwise.expression.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final String replacementExpression = calculateReplacementExpression(expression, new CommentTracker());
    return InspectionGadgetsBundle.message(
      "expression.can.be.replaced.problem.descriptor",
      replacementExpression);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("pointless.boolean.expression.ignore.option"),
      this, "m_ignoreExpressionsContainingConstants");
  }

  String calculateReplacementExpression(PsiExpression expression, CommentTracker ct) {
    if (expression instanceof PsiPolyadicExpression) {
      return calculateReplacementExpression((PsiPolyadicExpression)expression, ct);
    }
    if (expression instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      if (prefixExpression.getOperationTokenType().equals(TILDE)) {
        PsiExpression decremented = extractDecrementedValue(prefixExpression.getOperand());
        if (decremented != null) {
          return "-" + ct.text(decremented, ParenthesesUtils.PREFIX_PRECEDENCE);
        }
      }
    }
    return "";
  }

  @NonNls
  String calculateReplacementExpression(PsiPolyadicExpression expression, CommentTracker ct) {
    final IElementType tokenType = expression.getOperationTokenType();
    final PsiExpression[] operands = expression.getOperands();
    PsiExpression previousOperand = null;
    for (int i = 0, length = operands.length; i < length; i++) {
      final PsiExpression operand = operands[i];
      if (isZero(operand)) {
        if (tokenType.equals(AND) ||
            (tokenType.equals(LTLT) || tokenType.equals(GTGT) || tokenType.equals(GTGTGT)) && previousOperand == null) {
          return getText(expression, operands[0], operands[length - 1], PsiType.LONG.equals(expression.getType()) ? "0L" : "0", ct);
        }
        else if (tokenType.equals(OR) || tokenType.equals(XOR) ||
                 (tokenType.equals(LTLT) || tokenType.equals(GTGT) || tokenType.equals(GTGTGT)) && previousOperand != null) {
          return getText(expression, i == length - 1 ? expression.getTokenBeforeOperand(operand) : operand, ct);
        }
      }
      else if (isAllOnes(operand)) {
        if (tokenType.equals(AND)) {
          return getText(expression, i == length - 1 ? expression.getTokenBeforeOperand(operand) : operand, ct);
        }
        if (tokenType.equals(OR)) {
          return ct.text(operand);
        }
        else if (tokenType.equals(XOR)) {
          if (previousOperand != null) {
            return getText(expression, previousOperand, operand, getTildeReplacement(previousOperand, ct), ct);
          }
          else {
            final PsiExpression nextOperand = operands[i + 1];
            return getText(expression, operand, nextOperand, getTildeReplacement(nextOperand, ct), ct);
          }
        }
      }
      else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(previousOperand, operand)) {
        if (tokenType.equals(OR) || tokenType.equals(AND)) {
          return getText(expression, previousOperand, operand, ct.text(operand), ct);
        }
        else if (tokenType.equals(XOR)) {
          return getText(expression, previousOperand, operand, PsiType.LONG.equals(expression.getType()) ? "0L" : "0", ct);
        }
      }
      previousOperand = operand;
    }
    return "";
  }

  private static String getTildeReplacement(PsiExpression operand, CommentTracker ct) {
    PsiExpression decrementedValue = extractDecrementedValue(operand);
    if (decrementedValue != null) {
      return "-" + ct.text(decrementedValue, ParenthesesUtils.PREFIX_PRECEDENCE);
    }
    return "~" + ct.text(operand, ParenthesesUtils.PREFIX_PRECEDENCE);
  }

  private static PsiExpression extractDecrementedValue(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binOp = (PsiBinaryExpression)expression;
      if (binOp.getOperationTokenType().equals(MINUS)) {
        Number right = JavaPsiMathUtil.getNumberFromLiteral(binOp.getROperand());
        if ((right instanceof Integer || right instanceof Long) && right.longValue() == 1L) {
          return binOp.getLOperand();
        }
      }
    }
    return null;
  }

  private static String getText(PsiPolyadicExpression expression, PsiElement fromTarget, PsiElement untilTarget,
                                @NotNull @NonNls String replacement, CommentTracker ct) {
    final StringBuilder result = new StringBuilder();
    boolean stop = false;
    for (PsiElement child : expression.getChildren()) {
      if (child == fromTarget) {
        stop = true;
        result.append(replacement);
      }
      else if (child == untilTarget) {
        stop = false;
      }
      else if (child instanceof PsiComment || !stop) {
        result.append(ct.text(child));
      }
      else if (child instanceof PsiJavaToken && untilTarget == null) {
        stop = false;
      }
    }
    return result.toString();
  }

  private static String getText(PsiPolyadicExpression expression, PsiElement exclude, CommentTracker ct) {
    return getText(expression, exclude, null, "", ct).trim();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessBitwiseVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new PointlessBitwiseFix();
  }

  private class PointlessBitwiseFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "pointless.bitwise.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
      CommentTracker ct = new CommentTracker();
      final String newExpression = calculateReplacementExpression(expression, ct);
      if(!newExpression.isEmpty()) {
        ct.replaceAndRestoreComments(expression, newExpression);
      }
    }
  }

  private class PointlessBitwiseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (expression.getOperationTokenType().equals(TILDE) && extractDecrementedValue(expression.getOperand()) != null) {
        registerError(expression, expression);
      }
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType sign = expression.getOperationTokenType();
      if (!bitwiseTokens.contains(sign)) {
        return;
      }
      if (PsiUtilCore.hasErrorElementChild(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand == null) {
          return;
        }
        final PsiType type = operand.getType();
        if (type == null || type.equals(PsiType.BOOLEAN) ||
            type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
          return;
        }
      }
      final boolean isPointless;
      if (sign.equals(AND) || sign.equals(OR) || sign.equals(XOR)) {
        isPointless = booleanExpressionIsPointless(operands);
      }
      else if (sign.equals(LTLT) || sign.equals(GTGT) || sign.equals(GTGTGT)) {
        isPointless = shiftExpressionIsPointless(operands);
      }
      else {
        isPointless = false;
      }
      if (!isPointless) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean booleanExpressionIsPointless(PsiExpression[] operands) {
      PsiExpression previousExpression = null;
      for (PsiExpression operand : operands) {
        if (isZero(operand) || isAllOnes(operand) || (EquivalenceChecker.getCanonicalPsiEquivalence()
          .expressionsAreEquivalent(previousExpression, operand) && !SideEffectChecker.mayHaveSideEffects(operand))) {
          return true;
        }
        previousExpression = operand;
      }
      return false;
    }

    private boolean shiftExpressionIsPointless(PsiExpression[] operands) {
      for (PsiExpression operand : operands) {
        if (isZero(operand)) {
          return true;
        }
      }
      return false;
    }
  }

  private boolean isZero(PsiExpression expression) {
    if (m_ignoreExpressionsContainingConstants
        && !(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    return ExpressionUtils.isZero(expression);
  }

  private boolean isAllOnes(PsiExpression expression) {
    final PsiType expressionType = expression.getType();
    final Object value;
    if (m_ignoreExpressionsContainingConstants) {
      value = JavaPsiMathUtil.getNumberFromLiteral(expression);
    }
    else {
      value = ConstantExpressionUtil.computeCastTo(expression, expressionType);
    }
    if (value == null) {
      return false;
    }
    if (value instanceof Integer &&
        ((Integer)value).intValue() == 0xffffffff) {
      return true;
    }
    if (value instanceof Long &&
        ((Long)value).longValue() == 0xffffffffffffffffL) {
      return true;
    }
    if (value instanceof Short &&
        ((Short)value).shortValue() == (short)0xffff) {
      return true;
    }
    if (value instanceof Character &&
        ((Character)value).charValue() == (char)0xffff) {
      return true;
    }
    return value instanceof Byte &&
           ((Byte)value).byteValue() == (byte)0xff;
  }
}