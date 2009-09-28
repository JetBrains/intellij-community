package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.HashSet;
import java.util.Set;

public class GroovyPointlessArithmeticInspection extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return "Pointless arithmetic expression";
  }

  @NotNull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new PointlessArithmeticVisitor();
  }

  public String buildErrorString(Object... args) {
    return GroovyInspectionBundle.message("pointless.arithmetic.error.message", calculateReplacementExpression((GrExpression) args[0]));
  }

  private static String calculateReplacementExpression(GrExpression expression) {
    final GrBinaryExpression exp = (GrBinaryExpression) expression;
    final IElementType sign = exp.getOperationTokenType();
    final GrExpression lhs = exp.getLeftOperand();
    final GrExpression rhs = exp.getRightOperand();
    assert rhs != null;
    if (GroovyTokenTypes.mPLUS.equals(sign)) {
      if (isZero(lhs)) {
        return rhs.getText();
      } else {
        return lhs.getText();
      }
    } else if (GroovyTokenTypes.mMINUS.equals(sign)) {
      return lhs.getText();
    } else if (GroovyTokenTypes.mSTAR.equals(sign)) {
      if (isOne(lhs)) {
        return rhs.getText();
      } else if (isOne(rhs)) {
        return lhs.getText();
      } else {
        return "0";
      }
    } else if (GroovyTokenTypes.mDIV.equals(sign)) {
      return lhs.getText();
    } else {
      return "";
    }
  }

  public GroovyFix buildFix(PsiElement location) {
    return new PointlessArithmeticFix();
  }

  private class PointlessArithmeticFix extends GroovyFix {

    @NotNull
    public String getName() {
      return "Simplify";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final GrExpression expression = (GrExpression) descriptor
          .getPsiElement();
      final String newExpression =
          calculateReplacementExpression(expression);
      replaceExpression(expression, newExpression);
    }
  }

  private class PointlessArithmeticVisitor extends BaseInspectionVisitor {

    private final Set<IElementType> arithmeticTokens =
        new HashSet<IElementType>(4);

    {
      arithmeticTokens.add(GroovyTokenTypes.mPLUS);
      arithmeticTokens.add(GroovyTokenTypes.mMINUS);
      arithmeticTokens.add(GroovyTokenTypes.mSTAR);
      arithmeticTokens.add(GroovyTokenTypes.mDIV);
    }

    public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final GrExpression rhs = expression.getRightOperand();
      if (rhs == null) {
        return;
      }
      final IElementType sign = expression.getOperationTokenType();
      if (!arithmeticTokens.contains(sign)) {
        return;
      }
      final GrExpression lhs = expression.getLeftOperand();

      assert sign != null;
      final boolean isPointless;
      if (sign.equals(GroovyTokenTypes.mPLUS)) {
        isPointless = additionExpressionIsPointless(lhs, rhs);
      } else if (sign.equals(GroovyTokenTypes.mMINUS)) {
        isPointless = subtractionExpressionIsPointless(rhs);
      } else if (sign.equals(GroovyTokenTypes.mSTAR)) {
        isPointless = multiplyExpressionIsPointless(lhs, rhs);
      } else if (sign.equals(GroovyTokenTypes.mDIV)) {
        isPointless = divideExpressionIsPointless(rhs);
      } else {
        isPointless = false;
      }
      if (!isPointless) {
        return;
      }

      registerError(expression);
    }
  }

  private static boolean subtractionExpressionIsPointless(GrExpression rhs) {
    return isZero(rhs);
  }

  private static boolean additionExpressionIsPointless(GrExpression lhs,
                                                       GrExpression rhs) {
    return isZero(lhs) || isZero(rhs);
  }

  private static boolean multiplyExpressionIsPointless(GrExpression lhs,
                                                       GrExpression rhs) {
    return isZero(lhs) || isZero(rhs) || isOne(lhs) || isOne(rhs);
  }

  private static boolean divideExpressionIsPointless(GrExpression rhs) {
    return isOne(rhs);
  }

  /**
   * @noinspection FloatingPointEquality
   */
  private static boolean isZero(GrExpression expression) {
    @NonNls final String text = expression.getText();
    return "0".equals(text) ||
        "0x0".equals(text) ||
        "0X0".equals(text) ||
        "0.0".equals(text) ||
        "0L".equals(text) ||
        "0l".equals(text);
  }

  /**
   * @noinspection FloatingPointEquality
   */
  private static boolean isOne(GrExpression expression) {
    @NonNls final String text = expression.getText();
    return "1".equals(text) ||
        "0x1".equals(text) ||
        "0X1".equals(text) ||
        "1.0".equals(text) ||
        "1L".equals(text) ||
        "1l".equals(text);
  }
}