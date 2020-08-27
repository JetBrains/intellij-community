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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ComparisonUtils;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isFake;

public class GroovyPointlessBooleanInspection extends BaseInspection {

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessBooleanExpressionVisitor();
  }

  @Override
  public String buildErrorString(Object... args) {
    if (args[0] instanceof GrBinaryExpression) {
      return GroovyBundle.message("pointless.boolean.problem.descriptor", calculateSimplifiedBinaryExpression((GrBinaryExpression)args[0]));
    }
    else {
      return GroovyBundle.message("pointless.boolean.problem.descriptor", calculateSimplifiedPrefixExpression((GrUnaryExpression)args[0]));
    }
  }

  @Nullable
  private static String calculateSimplifiedBinaryExpression(GrBinaryExpression expression) {
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

  @NonNls
  private static String calculateSimplifiedPrefixExpression(GrUnaryExpression expression) {
    final GrExpression operand = expression.getOperand();
    if (isUnaryNot(operand)) {
      return booleanLiteral(((GrUnaryExpression)operand).getOperand());
    }
    else {
      return negateBooleanLiteral(operand);
    }
  }

  @NotNull
  private static String negateBooleanLiteral(GrExpression operand) {
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

  @NotNull
  private static String booleanLiteral(GrExpression operand) {
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
  public GroovyFix buildFix(@NotNull PsiElement location) {
    return new BooleanLiteralComparisonFix();
  }

  private static class BooleanLiteralComparisonFix extends GroovyFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return GroovyBundle.message("pointless.boolean.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof GrBinaryExpression) {
        final GrBinaryExpression expression = (GrBinaryExpression)element;
        final String replacementString = calculateSimplifiedBinaryExpression(expression);
        replaceExpression(expression, replacementString);
      }
      else {
        final GrUnaryExpression expression = (GrUnaryExpression)element;
        final String replacementString = calculateSimplifiedPrefixExpression(expression);
        replaceExpression(expression, replacementString);
      }
    }
  }

  private static class PointlessBooleanExpressionVisitor extends BaseInspectionVisitor {

    private static final TokenSet booleanTokens = TokenSet.create(T_LAND, T_LOR, T_XOR, T_EQ, T_NEQ);

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
    }
    else {
      return false;
    }
  }

  private static boolean equalityExpressionIsPointless(GrExpression lhs,
                                                       GrExpression rhs) {
    return (isTrue(lhs) || isFalse(lhs)) && isBoolean(rhs)
           || (isTrue(rhs) || isFalse(rhs)) && isBoolean(lhs);
  }

  private static boolean isBoolean(GrExpression expression) {
    final PsiType type = expression.getType();
    final PsiType unboxed = TypesUtil.unboxPrimitiveTypeWrapper(type);
    return unboxed != null && PsiType.BOOLEAN.equals(unboxed);
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
    @NonNls final String text = expression.getText();
    return "true".equals(text);
  }

  private static boolean isFalse(GrExpression expression) {
    if (expression == null) {
      return false;
    }
    if (!(expression instanceof GrLiteral)) {
      return false;
    }
    @NonNls final String text = expression.getText();
    return "false".equals(text);
  }
}
