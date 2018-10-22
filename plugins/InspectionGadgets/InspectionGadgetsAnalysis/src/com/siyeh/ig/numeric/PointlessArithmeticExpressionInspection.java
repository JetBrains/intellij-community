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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PointlessArithmeticExpressionInspection extends BaseInspection {

  private static final Set<IElementType> arithmeticTokens = new THashSet<>(9);

  static {
    arithmeticTokens.add(JavaTokenType.PLUS);
    arithmeticTokens.add(JavaTokenType.MINUS);
    arithmeticTokens.add(JavaTokenType.ASTERISK);
    arithmeticTokens.add(JavaTokenType.DIV);
    arithmeticTokens.add(JavaTokenType.PERC);
    arithmeticTokens.add(JavaTokenType.GT);
    arithmeticTokens.add(JavaTokenType.LT);
    arithmeticTokens.add(JavaTokenType.LE);
    arithmeticTokens.add(JavaTokenType.GE);
  }

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreExpressionsContainingConstants = true;

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "pointless.boolean.expression.ignore.option"),
      this, "m_ignoreExpressionsContainingConstants");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "pointless.arithmetic.expression.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("expression.can.be.replaced.problem.descriptor",
                                           calculateReplacementExpression((PsiPolyadicExpression)infos[0]));
  }

  @NonNls
  String calculateReplacementExpression(PsiPolyadicExpression expression) {
    final PsiExpression[] operands = expression.getOperands();
    final IElementType tokenType = expression.getOperationTokenType();
    final List<PsiExpression> expressions = collectSalientOperands(operands, tokenType, expression.getType());
    final PsiJavaToken token = expression.getTokenBeforeOperand(operands[1]);
    assert token != null;
    final String delimiter = " " + token.getText() + " ";
    return expressions.stream().map(PsiElement::getText).collect(Collectors.joining(delimiter));
  }

  @NotNull
  List<PsiExpression> collectSalientOperands(PsiExpression[] operands, IElementType tokenType, PsiType type) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(operands[0].getProject());
    final List<PsiExpression> expressions = new SmartList<>();
    for (int i = 0, length = operands.length; i < length; i++) {
      final PsiExpression operand = operands[i];
      if (tokenType.equals(JavaTokenType.PLUS) && isZero(operand) ||
        tokenType.equals(JavaTokenType.MINUS) && isZero(operand) && !expressions.isEmpty() ||
        tokenType.equals(JavaTokenType.ASTERISK) && isOne(operand) ||
        tokenType.equals(JavaTokenType.DIV) && isOne(operand) && !expressions.isEmpty()) {
        continue;
      }
      else if (tokenType.equals(JavaTokenType.MINUS) && i == 1 &&
               EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(ContainerUtil.getLastItem(expressions), operand)) {
        expressions.remove(expressions.size() - 1);
        expressions.add(factory.createExpressionFromText(PsiType.LONG.equals(type) ? "0L" : "0", operand));
        continue;
      }
      else if (tokenType.equals(JavaTokenType.DIV) &&
               EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(ContainerUtil.getLastItem(expressions), operand)) {
        expressions.remove(expressions.size() - 1);
        expressions.add(factory.createExpressionFromText(PsiType.LONG.equals(type) ? "1L" : "1", operand));
        continue;
      }
      else if (tokenType.equals(JavaTokenType.ASTERISK) && isZero(operand) ||
               tokenType.equals(JavaTokenType.PERC) &&
               (isOne(operand) || EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(ContainerUtil.getLastItem(expressions), operand))) {
        expressions.clear();
        expressions.add(factory.createExpressionFromText(PsiType.LONG.equals(type) ? "0L" : "0", operand));
        return expressions;
      }
      expressions.add(operand);
    }
    if (expressions.isEmpty()) {
      expressions.add(factory.createExpressionFromText(tokenType.equals(JavaTokenType.ASTERISK) ? "1" : "0", operands[0]));
    }
    return expressions;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new PointlessArithmeticFix();
  }

  private class PointlessArithmeticFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
      final PsiExpression[] operands = expression.getOperands();
      final PsiType type = expression.getType();
      final List<PsiExpression> expressions = collectSalientOperands(operands, expression.getOperationTokenType(), type);
      final CommentTracker tracker = new CommentTracker();
      final PsiJavaToken token = expression.getTokenBeforeOperand(operands[1]);
      assert token != null;
      final String delimiter = " " + token.getText() + " ";
      final String replacement = expressions.stream().map(x -> tracker.textWithComments(x)).collect(Collectors.joining(delimiter));
      final boolean castToLongNeeded = TypeConversionUtil.isLongType(type) &&
                                       expressions.stream().noneMatch(x -> TypeConversionUtil.isLongType(x.getType()));
      tracker.replaceExpressionAndRestoreComments(expression, castToLongNeeded ? "(long)" + replacement : replacement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessArithmeticVisitor();
  }

  private class PointlessArithmeticVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final PsiType expressionType = expression.getType();
      if (expressionType == null ||
          PsiType.DOUBLE.equals(expressionType) ||
          PsiType.FLOAT.equals(expressionType)) {
        return;
      }
      if (!arithmeticTokens.contains(expression.getOperationTokenType())) {
        return;
      }
      if (ExpressionUtils.hasStringType(expression) || PsiUtilCore.hasErrorElementChild(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      final IElementType tokenType = expression.getOperationTokenType();
      final boolean isPointless;
      if (tokenType.equals(JavaTokenType.PLUS)) {
        isPointless = additionExpressionIsPointless(operands);
      }
      else if (tokenType.equals(JavaTokenType.MINUS)) {
        isPointless = subtractionExpressionIsPointless(operands);
      }
      else if (tokenType.equals(JavaTokenType.ASTERISK)) {
        isPointless = multiplyExpressionIsPointless(operands);
      }
      else if (tokenType.equals(JavaTokenType.DIV) || tokenType.equals(JavaTokenType.PERC)) {
        isPointless = divideExpressionIsPointless(operands);
      }
      else {
        isPointless = false;
      }
      if (!isPointless) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean subtractionExpressionIsPointless(PsiExpression[] expressions) {
      PsiExpression previousExpression = null;
      for (int i = 0; i < expressions.length; i++) {
        final PsiExpression expression = expressions[i];
        if (previousExpression != null &&
            (isZero(expression) || areExpressionsIdenticalWithoutSideEffects(previousExpression, expression, i))) {
          return true;
        }
        previousExpression = expression;
      }
      return false;
    }

    private boolean additionExpressionIsPointless(PsiExpression[] expressions) {
      for (PsiExpression expression : expressions) {
        if (isZero(expression)) {
          return true;
        }
      }
      return false;
    }

    private boolean multiplyExpressionIsPointless(PsiExpression[] expressions) {
      for (PsiExpression expression : expressions) {
        if (isZero(expression) || isOne(expression)) {
          return true;
        }
      }
      return false;
    }

    private boolean divideExpressionIsPointless(PsiExpression[] expressions) {
      PsiExpression previousExpression = null;
      for (int i = 0; i < expressions.length; i++) {
        final PsiExpression expression = expressions[i];
        if (previousExpression != null &&
            (isOne(expression) || areExpressionsIdenticalWithoutSideEffects(previousExpression, expression, i) && !isZero(expression))) {
          return true;
        }
        previousExpression = expression;
      }
      return false;
    }

    private boolean areExpressionsIdenticalWithoutSideEffects(PsiExpression expression1, PsiExpression expression2, int index) {
      return index == 1 && EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(expression1, expression2) &&
             !SideEffectChecker.mayHaveSideEffects(expression1);
    }
  }

  boolean isZero(PsiExpression expression) {
    if (m_ignoreExpressionsContainingConstants && PsiUtil.deparenthesizeExpression(expression) instanceof PsiReferenceExpression) {
      return false;
    }
    return ExpressionUtils.isZero(expression);
  }

  boolean isOne(PsiExpression expression) {
    if (m_ignoreExpressionsContainingConstants && PsiUtil.deparenthesizeExpression(expression) instanceof PsiReferenceExpression) {
      return false;
    }
    return ExpressionUtils.isOne(expression);
  }
}