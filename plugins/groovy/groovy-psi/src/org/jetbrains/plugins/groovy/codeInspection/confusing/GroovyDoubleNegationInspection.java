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
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyDoubleNegationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return "Double negation";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return "Double negation #ref #loc";
  }

  @Override
  @Nullable
  protected GroovyFix buildFix(@NotNull PsiElement location) {
    return new DoubleNegationFix();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static class DoubleNegationFix extends GroovyFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return "Remove double negation";
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final GrUnaryExpression expression =
          (GrUnaryExpression) descriptor.getPsiElement();
      GrExpression operand = (GrExpression)PsiUtil.skipParentheses(expression.getOperand(), false);
      if (operand instanceof GrUnaryExpression) {
        final GrUnaryExpression prefixExpression =
            (GrUnaryExpression) operand;
        final GrExpression innerOperand = prefixExpression.getOperand();
        if (innerOperand == null) {
          return;
        }
        replaceExpression(expression, innerOperand.getText());
      } else if (operand instanceof GrBinaryExpression) {
        final GrBinaryExpression binaryExpression =
            (GrBinaryExpression) operand;
        final GrExpression lhs = binaryExpression.getLeftOperand();
        final String lhsText = lhs.getText();
        final StringBuilder builder =
            new StringBuilder(lhsText);
        builder.append("==");
        final GrExpression rhs = binaryExpression.getRightOperand();
        if (rhs != null) {
          final String rhsText = rhs.getText();
          builder.append(rhsText);
        }
        replaceExpression(expression, builder.toString());
      }
    }
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DoubleNegationVisitor();
  }

  private static class DoubleNegationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitUnaryExpression(@NotNull GrUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!GroovyTokenTypes.mLNOT.equals(tokenType)) {
        return;
      }
      checkParent(expression);
    }

    @Override
    public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!GroovyTokenTypes.mNOT_EQUAL.equals(tokenType)) {
        return;
      }
      checkParent(expression);
    }

    private void checkParent(GrExpression expression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof GrParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof GrUnaryExpression)) {
        return;
      }
      final GrUnaryExpression prefixExpression =
          (GrUnaryExpression) parent;
      final IElementType parentTokenType =
          prefixExpression.getOperationTokenType();
      if (!GroovyTokenTypes.mLNOT.equals(parentTokenType)) {
        return;
      }
      registerError(prefixExpression);
    }
  }
}