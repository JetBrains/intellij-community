/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
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
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("pointless.boolean.expression.ignore.option"),
                                          this, "m_ignoreExpressionsContainingConstants");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("expression.can.be.replaced.problem.descriptor",
                                           calculateReplacementExpression((PsiPolyadicExpression)infos[0], null));
  }

  @NonNls
  String calculateReplacementExpression(PsiPolyadicExpression expression, @Nullable CommentTracker ct) {
    final PsiExpression[] operands = expression.getOperands();
    final IElementType tokenType = expression.getOperationTokenType();
    final PsiType type = expression.getType();
    final List<PsiExpression> expressions = collectSalientOperands(operands, tokenType, type);
    final PsiJavaToken token = expression.getTokenBeforeOperand(operands[1]);
    assert token != null;
    String prefix = "";
    if (isZero(expressions.get(0)) && expressions.size() > 1 && JavaTokenType.MINUS == token.getTokenType()) {
      expressions.remove(0);
      prefix = "- ";
    }
    final String delimiter = " " + token.getText() + " ";
    final String result =
      prefix + expressions.stream().map(e -> ct == null ? e.getText() : ct.textWithComments(e)).collect(Collectors.joining(delimiter));
    final boolean castToLongNeeded = ct != null && TypeConversionUtil.isLongType(type) &&
                                     expressions.stream().noneMatch(x -> TypeConversionUtil.isLongType(x.getType()));
    return castToLongNeeded ? "(long)" + result : result;
  }

  @NotNull
  List<PsiExpression> collectSalientOperands(PsiExpression[] operands, IElementType tokenType, PsiType type) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(operands[0].getProject());
    final List<PsiExpression> expressions = new SmartList<>();
    for (PsiExpression operand : operands) {
      if (tokenType.equals(JavaTokenType.PLUS) && isZero(operand) ||
          tokenType.equals(JavaTokenType.MINUS) && isZero(operand) && !expressions.isEmpty() ||
          tokenType.equals(JavaTokenType.ASTERISK) && isOne(operand) ||
          tokenType.equals(JavaTokenType.DIV) && isOne(operand) && !expressions.isEmpty()) {
        continue;
      }
      else if (tokenType.equals(JavaTokenType.MINUS) && !expressions.isEmpty() &&
               EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(expressions.get(0), operand)) {
        expressions.remove(0);
        expressions.add(0, factory.createExpressionFromText(numberAsText(0, type), operand));
        continue;
      }
      else if (tokenType.equals(JavaTokenType.DIV) &&
               EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(ContainerUtil.getLastItem(expressions), operand)) {
        expressions.remove(expressions.size() - 1);
        expressions.add(factory.createExpressionFromText(numberAsText(1, type), operand));
        continue;
      }
      else if (tokenType.equals(JavaTokenType.ASTERISK) && isZero(operand) ||
               tokenType.equals(JavaTokenType.PERC) &&
               (isOne(operand) || EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(ContainerUtil.getLastItem(expressions), operand))) {
        expressions.clear();
        expressions.add(factory.createExpressionFromText(numberAsText(0, type), operand));
        return expressions;
      }
      expressions.add(operand);
    }
    if (expressions.isEmpty()) {
      final int value = tokenType.equals(JavaTokenType.ASTERISK) ? 1 : 0;
      expressions.add(factory.createExpressionFromText(numberAsText(value, type), operands[0]));
    }
    return expressions;
  }

  @NotNull
  private static String numberAsText(int num, PsiType type) {
    if (PsiType.DOUBLE.equals(type)) return num + ".0";
    if (PsiType.FLOAT.equals(type)) return num + ".0f";
    if (PsiType.LONG.equals(type)) return num + "L";
    return String.valueOf(num);
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new PointlessArithmeticFix();
  }

  private class PointlessArithmeticFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
      final CommentTracker tracker = new CommentTracker();
      tracker.replaceExpressionAndRestoreComments(expression, calculateReplacementExpression(expression, tracker));
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
      if (!arithmeticTokens.contains(expression.getOperationTokenType())) {
        return;
      }
      if (ExpressionUtils.hasStringType(expression) || PsiUtilCore.hasErrorElementChild(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      final IElementType tokenType = expression.getOperationTokenType();
      final boolean isPointless;
      final PsiType expressionType = expression.getType();
      if (expressionType == null) return;
      if (PsiType.DOUBLE.equals(expressionType) || PsiType.FLOAT.equals(expressionType)) {
        isPointless = floatingPointOperationIsPointless(tokenType, operands);
      }
      else if (tokenType.equals(JavaTokenType.PLUS)) {
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

    private boolean floatingPointOperationIsPointless(IElementType type, PsiExpression[] expressions) {
      if (expressions.length != 2) return false;
      if (type.equals(JavaTokenType.MINUS)) {
        return areExpressionsIdenticalWithoutSideEffects(expressions[0], expressions[1]);
      }
      if (type.equals(JavaTokenType.DIV) || type.equals(JavaTokenType.PERC)) {
        return areExpressionsIdenticalWithoutSideEffects(expressions[0], expressions[1]) ||
               isOne(expressions[1]);
      }
      if (type.equals(JavaTokenType.ASTERISK)) {
        return (isOne(expressions[0]) || isOne(expressions[1])) && 
               Objects.requireNonNull(expressions[0].getType()).equals(expressions[1].getType());
      }
      return false;
    }

    private boolean subtractionExpressionIsPointless(PsiExpression[] expressions) {
      final PsiExpression firstExpression = expressions[0];
      if (isZero(firstExpression)) {
        return true;
      }
      for (int i = 1; i < expressions.length; i++) {
        final PsiExpression expression = expressions[i];
        if (isZero(expression) || areExpressionsIdenticalWithoutSideEffects(firstExpression, expression)) {
          return true;
        }
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
            (isOne(expression) || i == 1 && areExpressionsIdenticalWithoutSideEffects(previousExpression, expression) && !isZero(expression))) {
          return true;
        }
        previousExpression = expression;
      }
      return false;
    }

    private boolean areExpressionsIdenticalWithoutSideEffects(PsiExpression expression1, PsiExpression expression2) {
      return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(expression1, expression2) &&
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