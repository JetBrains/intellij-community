/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class PointlessBitwiseExpressionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreExpressionsContainingConstants = false;

  static final Set<IElementType> bitwiseTokens =
    new HashSet<>(6);

  static {
    bitwiseTokens.add(JavaTokenType.AND);
    bitwiseTokens.add(JavaTokenType.OR);
    bitwiseTokens.add(JavaTokenType.XOR);
    bitwiseTokens.add(JavaTokenType.LTLT);
    bitwiseTokens.add(JavaTokenType.GTGT);
    bitwiseTokens.add(JavaTokenType.GTGTGT);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "pointless.bitwise.expression.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiPolyadicExpression polyadicExpression =
      (PsiPolyadicExpression)infos[0];
    final String replacementExpression =
      calculateReplacementExpression(polyadicExpression);
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

  @NonNls
  String calculateReplacementExpression(PsiPolyadicExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    final PsiExpression[] operands = expression.getOperands();
    PsiExpression previousOperand = null;
    for (int i = 0, length = operands.length; i < length; i++) {
      final PsiExpression operand = operands[i];
      if (isZero(operand)) {
        if (tokenType.equals(JavaTokenType.AND) ||
            (tokenType.equals(JavaTokenType.LTLT) || tokenType.equals(JavaTokenType.GTGT) || tokenType.equals(JavaTokenType.GTGTGT)) &&
            previousOperand == null) {
          return getText(expression, operands[0], operands[length - 1], PsiType.LONG.equals(expression.getType()) ? "0L" : "0");
        }
        else if (tokenType.equals(JavaTokenType.OR) || tokenType.equals(JavaTokenType.XOR) ||
          (tokenType.equals(JavaTokenType.LTLT) || tokenType.equals(JavaTokenType.GTGT) || tokenType.equals(JavaTokenType.GTGTGT)) &&
          previousOperand != null) {
          return getText(expression, i == length - 1 ? expression.getTokenBeforeOperand(operand) : operand);
        }
      }
      else if (isAllOnes(operand)) {
        if (tokenType.equals(JavaTokenType.AND)) {
          return getText(expression, i == length - 1 ? expression.getTokenBeforeOperand(operand) : operand);
        }
        if (tokenType.equals(JavaTokenType.OR)) {
          return operand.getText();
        }
        else if (tokenType.equals(JavaTokenType.XOR)) {
          if (previousOperand != null) {
            return getText(expression, previousOperand, operand, "~" + previousOperand.getText());
          }
          else {
            final PsiExpression nextOperand = operands[i + 1];
            return getText(expression, operand, nextOperand, "~" + nextOperand.getText());
          }
        }
      }
      else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(previousOperand, operand)) {
        if (tokenType.equals(JavaTokenType.OR) || tokenType.equals(JavaTokenType.AND)) {
          return getText(expression, previousOperand, operand, operand.getText());
        }
        else if (tokenType.equals(JavaTokenType.XOR)) {
          return getText(expression, previousOperand, operand, PsiType.LONG.equals(expression.getType()) ? "0L" : "0");
        }
      }
      previousOperand = operand;
    }
    return "";
  }

  public static String getText(PsiPolyadicExpression expression, PsiElement fromTarget, PsiElement untilTarget,
                               @NotNull @NonNls String replacement) {
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
        result.append(child.getText());
      }
      else if (child instanceof PsiJavaToken && untilTarget == null) {
        stop = false;
      }
    }
    return result.toString();
  }

  private static String getText(PsiPolyadicExpression expression, PsiElement exclude) {
    return getText(expression, exclude, null, "").trim();
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
    public String getName() {
      return InspectionGadgetsBundle.message(
        "pointless.bitwise.expression.simplify.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiPolyadicExpression expression = (PsiPolyadicExpression)
        descriptor.getPsiElement();
      final String newExpression =
        calculateReplacementExpression(expression);
      PsiReplacementUtil.replaceExpression(expression, newExpression);
    }
  }

  private class PointlessBitwiseVisitor extends BaseInspectionVisitor {

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
      if (sign.equals(JavaTokenType.AND) || sign.equals(JavaTokenType.OR) ||
          sign.equals(JavaTokenType.XOR)) {
        isPointless = booleanExpressionIsPointless(operands);
      }
      else if (sign.equals(JavaTokenType.LTLT) ||
               sign.equals(JavaTokenType.GTGT) ||
               sign.equals(JavaTokenType.GTGTGT)) {
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
    if (m_ignoreExpressionsContainingConstants
        && !(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiType expressionType = expression.getType();
    final Object value =
      ConstantExpressionUtil.computeCastTo(expression,
                                           expressionType);
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