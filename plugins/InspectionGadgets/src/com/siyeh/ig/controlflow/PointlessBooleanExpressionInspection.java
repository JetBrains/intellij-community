/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class PointlessBooleanExpressionInspection extends BaseInspection {

  private static final Set<IElementType> booleanTokens = new HashSet<IElementType>(7);

  static {
    booleanTokens.add(JavaTokenType.ANDAND);
    booleanTokens.add(JavaTokenType.AND);
    booleanTokens.add(JavaTokenType.OROR);
    booleanTokens.add(JavaTokenType.OR);
    booleanTokens.add(JavaTokenType.XOR);
    booleanTokens.add(JavaTokenType.EQEQ);
    booleanTokens.add(JavaTokenType.NE);
  }


  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreExpressionsContainingConstants = false;

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message("pointless.boolean.expression.ignore.option"),
      this,
      "m_ignoreExpressionsContainingConstants"
    );
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("pointless.boolean.expression.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    if (infos[0] instanceof PsiPolyadicExpression) {
      if (infos[0] instanceof PsiBinaryExpression) {
        final PsiBinaryExpression expression = (PsiBinaryExpression)infos[0];
        return InspectionGadgetsBundle.message(
          "boolean.expression.can.be.simplified.problem.descriptor",
          calculateSimplifiedBinaryExpression(expression)
        );
      }
      return InspectionGadgetsBundle.message("boolean.expression.can.be.simplified.polyadic.problem.descriptor");
    }
    else {
      final PsiPrefixExpression expression = (PsiPrefixExpression)infos[0];
      return InspectionGadgetsBundle.message(
        "boolean.expression.can.be.simplified.problem.descriptor",
        calculateSimplifiedPrefixExpression(expression)
      );
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessBooleanExpressionVisitor();
  }

  @Nullable
  private String calculateSimplifiedBinaryExpression(
    PsiBinaryExpression expression) {
    final PsiExpression lhs = expression.getLOperand();

    final PsiExpression rhs = expression.getROperand();
    if (rhs == null) {
      return null;
    }
    final IElementType tokenType = expression.getOperationTokenType();
    final String rhsText = rhs.getText();
    final String lhsText = lhs.getText();
    if (tokenType.equals(JavaTokenType.ANDAND) ||
        tokenType.equals(JavaTokenType.AND)) {
      if (isAlwaysTrue(lhs)) {
        return rhsText;
      }
      else if (isAlwaysFalse(lhs) || isAlwaysFalse(rhs)) {
        return "false";
      }
      else {
        return lhsText;
      }
    }
    else if (tokenType.equals(JavaTokenType.OROR) ||
             tokenType.equals(JavaTokenType.OR)) {
      if (isAlwaysFalse(lhs)) {
        return rhsText;
      }
      else {
        return lhsText;
      }
    }
    else if (tokenType.equals(JavaTokenType.XOR) ||
             tokenType.equals(JavaTokenType.NE)) {
      if (isAlwaysFalse(lhs)) {
        return rhsText;
      }
      else if (isAlwaysFalse(rhs)) {
        return lhsText;
      }
      else if (isAlwaysTrue(lhs)) {
        return createStringForNegatedExpression(rhs);
      }
      else {
        return createStringForNegatedExpression(lhs);
      }
    }
    else if (tokenType.equals(JavaTokenType.EQEQ)) {
      if (isAlwaysTrue(lhs)) {
        return rhsText;
      }
      else if (isAlwaysTrue(rhs)) {
        return lhsText;
      }
      else if (isAlwaysFalse(lhs)) {
        return createStringForNegatedExpression(rhs);
      }
      else {
        return createStringForNegatedExpression(lhs);
      }
    }
    else {
      return "";
    }
  }

  private static String createStringForNegatedExpression(PsiExpression exp) {
    if (ComparisonUtils.isComparison(exp)) {
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)exp;
      final String negatedComparison =
        ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      assert rhs != null;
      return lhs.getText() + negatedComparison + rhs.getText();
    }
    else {
      if (ParenthesesUtils.getPrecedence(exp) >
          ParenthesesUtils.PREFIX_PRECEDENCE) {
        return "!(" + exp.getText() + ')';
      }
      else {
        return '!' + exp.getText();
      }
    }
  }

  @NotNull
  private String calculateSimplifiedPrefixExpression(PsiPrefixExpression e) {
    final PsiExpression expression = removeRedundantNots(e);

    final Boolean value = evaluate(expression);

    if (value == Boolean.TRUE) {
      return PsiKeyword.TRUE;
    }

    if (value == Boolean.FALSE) {
      return PsiKeyword.FALSE;
    }

    return expression != null ? expression.getText() : "";
  }

  @Nullable
  private static PsiExpression removeRedundantNots(@NotNull PsiPrefixExpression expression) {
    if (!JavaTokenType.EXCL.equals(expression.getOperationTokenType())) {
      return expression;
    }

    final PsiExpression operand = expression.getOperand();
    if (operand == null || !(operand instanceof PsiPrefixExpression)) {
      return expression;
    }

    final PsiPrefixExpression prefixOperand = (PsiPrefixExpression)operand;
    if (!JavaTokenType.EXCL.equals(prefixOperand.getOperationTokenType())) {
      return expression;
    }

    final PsiExpression op = prefixOperand.getOperand();
    return op != null && op instanceof PsiPrefixExpression ? removeRedundantNots((PsiPrefixExpression)op) : op;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new BooleanLiteralComparisonFix();
  }

  private class BooleanLiteralComparisonFix extends InspectionGadgetsFix {
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      processSubExpressions(project, (PsiExpression)element);
    }

    private boolean processSubExpressions(Project project, PsiExpression element) {
      if (element instanceof PsiPrefixExpression) {
        return processPrefixExpression(project, (PsiPrefixExpression)element);
      }

      if (element instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element;
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          final Boolean bool = evaluate(operand);
          if (bool != null) {
            SimplifyBooleanExpressionFix.simplifyExpression(project, operand, bool);
            return true;
          }
          if (processSubExpressions(project, operand)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean processPrefixExpression(Project project, PsiPrefixExpression expression) {
      final Boolean value = evaluate(expression);

      if (value == null && JavaTokenType.EXCL.equals(expression.getOperationTokenType())) {
        final PsiExpression fixed = removeRedundantNots(expression);
        if (fixed != null && fixed != expression) {
          expression.replace(fixed);
        }
      }
      else {
        SimplifyBooleanExpressionFix.simplifyExpression(project, expression, value);
      }
      return false;
    }
  }

  private class PointlessBooleanExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      //to avoid drilldown
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType sign = expression.getOperationTokenType();
      if (!booleanTokens.contains(sign)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand == null) {
          return;
        }
        final PsiType opType = operand.getType();
        if (opType == null) {
          return;
        }
        if (!opType.equals(PsiType.BOOLEAN) &&
            !opType.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
          return;
        }
      }

      final boolean isPointless;
      if (sign.equals(JavaTokenType.EQEQ) ||
          sign.equals(JavaTokenType.NE)) {
        isPointless = equalityExpressionIsPointless(operands);
      }
      else if (sign.equals(JavaTokenType.ANDAND) ||
               sign.equals(JavaTokenType.AND)) {
        isPointless = andExpressionIsPointless(operands);
      }
      else if (sign.equals(JavaTokenType.OROR) ||
               sign.equals(JavaTokenType.OR)) {
        isPointless = orExpressionIsPointless(operands);
      }
      else if (sign.equals(JavaTokenType.XOR)) {
        isPointless = xorExpressionIsPointless(operands);
      }
      else {
        isPointless = false;
      }
      if (!isPointless) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      final PsiExpression operand = expression.getOperand();
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.EXCL.equals(tokenType) && notExpressionIsPointless(operand)) {
        registerError(expression, expression);
      }
    }

    private boolean equalityExpressionIsPointless(PsiExpression... lhs) {
      for (PsiExpression expression : lhs) {
        if (evaluate(expression) != null) return true;
      }
      return false;
    }

    private boolean andExpressionIsPointless(PsiExpression... lhs) {
      return equalityExpressionIsPointless(lhs);
    }

    private boolean orExpressionIsPointless(PsiExpression... lhs) {
      for (PsiExpression expression : lhs) {
        if (isAlwaysFalse(expression)) return true;
      }
      return false;
    }

    private boolean xorExpressionIsPointless(PsiExpression... lhs) {
      return equalityExpressionIsPointless(lhs);
    }

    private boolean notExpressionIsPointless(PsiExpression arg) {
      if (arg instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)arg;
        if (JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
          return true;
        }
      }
      return equalityExpressionIsPointless(arg);
    }
  }

  @Nullable
  private Boolean evaluate(@Nullable PsiExpression expression) {
    if (m_ignoreExpressionsContainingConstants && !(expression instanceof PsiLiteralExpression)) {
      return false;
    }

    if (expression == null) {
      return null;
    }
    final Boolean value = (Boolean)ConstantExpressionUtil.computeCastTo(expression, PsiType.BOOLEAN);
    return value != null ? value.booleanValue() : null;
  }

  private boolean isAlwaysTrue(@Nullable PsiExpression expression) {
    return evaluate(expression) == Boolean.TRUE;
  }

  private boolean isAlwaysFalse(@Nullable PsiExpression expression) {
    return evaluate(expression) == Boolean.FALSE;
  }
}