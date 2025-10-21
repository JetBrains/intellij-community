/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ComparisonUtils;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isFake;

public final class GroovyPointlessBooleanInspection extends BaseInspection {

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new PointlessBooleanExpressionVisitor();
  }

  @Override
  public String buildErrorString(Object... args) {
    return GroovyBundle.message("pointless.boolean.problem.descriptor");
  }

  private static @Nullable String calculateSimplifiedBinaryExpression(GrBinaryExpression expression) {
    final IElementType sign = expression.getOperationTokenType();
    final GrExpression lhs = expression.getLeftOperand();

    final GrExpression rhs = expression.getRightOperand();
    if (rhs == null) {
      return null;
    }
    final String rhsText = rhs.getText();
    final String lhsText = lhs.getText();
    if (sign.equals(T_LAND)) {
      if (isTrue(lhs)) {
        return rhsText;
      }
      else {
        return lhsText;
      }
    }
    else if (sign.equals(T_LOR)) {
      if (isFalse(lhs)) {
        return rhsText;
      }
      else {
        return lhsText;
      }
    } else if (sign.equals(T_IMPL)) {
      if (isFalse(lhs)) {
        return rhsText;
      } else  {
        return lhsText;
      }
    }
    else if (sign.equals(T_XOR) || sign.equals(T_NEQ)) {
      if (isFalse(lhs)) {
        return rhsText;
      }
      else if (isFalse(rhs)) {
        return lhsText;
      }
      else if (isTrue(lhs)) {
        return createStringForNegatedExpression(rhs);
      }
      else {
        return createStringForNegatedExpression(lhs);
      }
    }
    else if (sign.equals(T_EQ)) {
      if (isTrue(lhs)) {
        return rhsText;
      }
      else if (isTrue(rhs)) {
        return lhsText;
      }
      else if (isFalse(lhs)) {
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

  private static String createStringForNegatedExpression(GrExpression exp) {
    if (ComparisonUtils.isComparison(exp)) {
      final GrBinaryExpression binaryExpression = (GrBinaryExpression)exp;
      final IElementType sign = binaryExpression.getOperationTokenType();
      final String negatedComparison = ComparisonUtils.getNegatedComparison(sign);
      final GrExpression lhs = binaryExpression.getLeftOperand();
      final GrExpression rhs = binaryExpression.getRightOperand();
      if (rhs == null) {
        return lhs.getText() + negatedComparison;
      }
      return lhs.getText() + negatedComparison + rhs.getText();
    }
    else {
      final String baseText = exp.getText();
      if (ParenthesesUtils.getPrecedence(exp) > ParenthesesUtils.PREFIX_PRECEDENCE) {
        return "!(" + baseText + ')';
      }
      else {
        return '!' + baseText;
      }
    }
  }

  private static @NonNls String calculateSimplifiedPrefixExpression(GrUnaryExpression expression) {
    final GrExpression operand = expression.getOperand();
    if (isUnaryNot(operand)) {
      return booleanLiteral(((GrUnaryExpression)operand).getOperand());
    }
    else {
      return negateBooleanLiteral(operand);
    }
  }

  private static @NotNull String negateBooleanLiteral(GrExpression operand) {
    if (isTrue(operand)) {
      return "false";
    }
    else if (isFalse(operand)) {
      return "true";
    }
    else {
      throw new IllegalStateException(operand.getText());
    }
  }

  private static @NotNull String booleanLiteral(GrExpression operand) {
    if (isTrue(operand)) {
      return "true";
    }
    else if (isFalse(operand)) {
      return "false";
    }
    else {
      throw new IllegalStateException(operand.getText());
    }
  }

  @Override
  public LocalQuickFix buildFix(@NotNull PsiElement location) {
    return new BooleanLiteralComparisonFix();
  }

  private static class BooleanLiteralComparisonFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("pointless.boolean.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof GrBinaryExpression expression) {
        final String replacementString = calculateSimplifiedBinaryExpression(expression);
        GrInspectionUtil.replaceExpression(expression, replacementString);
      }
      else {
        final GrUnaryExpression expression = (GrUnaryExpression)element;
        final String replacementString = calculateSimplifiedPrefixExpression(expression);
        GrInspectionUtil.replaceExpression(expression, replacementString);
      }
    }
  }

  private static class PointlessBooleanExpressionVisitor extends BaseInspectionVisitor {

    private static final TokenSet booleanTokens = TokenSet.create(T_LAND, T_LOR, T_XOR, T_EQ, T_NEQ, T_IMPL);

    @Override
    public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
      if (isFake(expression)) return;

      final IElementType sign = expression.getOperationTokenType();
      if (!booleanTokens.contains(sign)) {
        return;
      }

      final GrExpression rhs = expression.getRightOperand();
      if (rhs == null) {
        return;
      }

      final GrExpression lhs = expression.getLeftOperand();

      if (isPointless(sign, rhs, lhs)) {
        registerError(expression);
      }
    }

    @Override
    public void visitUnaryExpression(@NotNull GrUnaryExpression expression) {
      final IElementType sign = expression.getOperationTokenType();
      if (!sign.equals(T_NOT)) {
        return;
      }
      final GrExpression operand = expression.getOperand();
      if (isBooleanLiteral(operand)) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof GrExpression && isUnaryNot((GrExpression)parent)) {
          // don't highlight inner unary in double negation
          return;
        }
        registerError(expression);
      }
      else if (isUnaryNot(operand) && isBooleanLiteral(((GrUnaryExpression)operand).getOperand())) {
        registerError(expression);
      }
    }
  }

  private static boolean isPointless(IElementType sign, GrExpression rhs, GrExpression lhs) {
    if (sign.equals(T_EQ) || sign.equals(T_NEQ)) {
      return equalityExpressionIsPointless(lhs, rhs);
    }
    else if (sign.equals(T_LAND)) {
      return andExpressionIsPointless(lhs, rhs);
    }
    else if (sign.equals(T_LOR)) {
      return orExpressionIsPointless(lhs, rhs);
    }
    else if (sign.equals(T_XOR)) {
      return xorExpressionIsPointless(lhs, rhs);
    } else if(sign.equals(T_IMPL)) {
      return implExpressionIsPointless(lhs, rhs);
    }
    else {
      return false;
    }
  }

  private static boolean implExpressionIsPointless(@NotNull GrExpression lhs, @NotNull GrExpression rhs) {
    return isFalse(lhs) || isTrue(rhs);
  }

  private static boolean equalityExpressionIsPointless(GrExpression lhs,
                                                       GrExpression rhs) {
    return ((isTrue(lhs) || isFalse(lhs)) && isBoolean(rhs)) ||
           ((isTrue(rhs) || isFalse(rhs)) && isBoolean(lhs));
  }

  private static boolean isBoolean(GrExpression expression) {
    final PsiType type = expression.getType();
    return PsiTypes.booleanType().equals(type);
  }

  private static boolean andExpressionIsPointless(GrExpression lhs,
                                                  GrExpression rhs) {
    return isTrue(lhs) || isTrue(rhs);
  }

  private static boolean orExpressionIsPointless(GrExpression lhs,
                                                 GrExpression rhs) {
    return isFalse(lhs) || isFalse(rhs);
  }

  private static boolean xorExpressionIsPointless(GrExpression lhs,
                                                  GrExpression rhs) {
    return isTrue(lhs) || isTrue(rhs) || isFalse(lhs) || isFalse(rhs);
  }

  private static boolean isUnaryNot(GrExpression arg) {
    return arg instanceof GrUnaryExpression && ((GrUnaryExpression)arg).getOperationTokenType() == T_NOT;
  }

  private static boolean isBooleanLiteral(GrExpression arg) {
    return isFalse(arg) || isTrue(arg);
  }

  private static boolean isTrue(GrExpression expression) {
    if (expression == null) {
      return false;
    }
    if (!(expression instanceof GrLiteral)) {
      return false;
    }
    final @NonNls String text = expression.getText();
    return "true".equals(text);
  }

  private static boolean isFalse(GrExpression expression) {
    if (expression == null) {
      return false;
    }
    if (!(expression instanceof GrLiteral)) {
      return false;
    }
    final @NonNls String text = expression.getText();
    return "false".equals(text);
  }
}
