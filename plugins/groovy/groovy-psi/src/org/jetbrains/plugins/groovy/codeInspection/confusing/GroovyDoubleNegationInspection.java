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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.BoolUtils.isBooleanExpression;

public class GroovyDoubleNegationInspection extends BaseInspection {
  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

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
    return new GroovyFix() {
      @Override
      @NotNull
      public String getName() {
        return "Remove double negation";
      }

      @Override
      protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
        final GrUnaryExpression expression = (GrUnaryExpression)descriptor.getPsiElement();
        GrExpression operand = (GrExpression)PsiUtil.skipParentheses(expression.getOperand(), false);
        if (operand instanceof GrUnaryExpression)
          fixUnary(expression, (GrUnaryExpression)operand);
        else if (operand instanceof GrBinaryExpression)
          fixBinary(expression, (GrBinaryExpression)operand);
      }

      private void fixBinary(GrUnaryExpression expression, GrBinaryExpression operand) {
        final GrExpression lhs = operand.getLeftOperand();

        final StringBuilder builder = new StringBuilder(lhs.getText());
        builder.append("==");
        final GrExpression rhs = operand.getRightOperand();
        if (rhs != null) {
          builder.append(rhs.getText());
        }
        replaceExpression(expression, builder.toString());
      }

      private void fixUnary(GrUnaryExpression expression, GrUnaryExpression prefixExpression) {
        final GrExpression innerOperand = prefixExpression.getOperand();
        if (innerOperand == null) return;

        replaceExpression(expression, innerOperand.getText());
      }
    };
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitUnaryExpression(GrUnaryExpression expression) {
        super.visitUnaryExpression(expression);
        if (!GroovyTokenTypes.mLNOT.equals(expression.getOperationTokenType()) ||
            !(isBooleanExpression(expression.getOperand()) || isPartOfConditionalStatement(expression))) return;

        checkParent(expression);
      }

      @Override
      public void visitBinaryExpression(GrBinaryExpression expression) {
        super.visitBinaryExpression(expression);
        if (!GroovyTokenTypes.mNOT_EQUAL.equals(expression.getOperationTokenType())) return;

        checkParent(expression);
      }

      private boolean isPartOfConditionalStatement(GrUnaryExpression expression) {
        PsiElement parent = expression.getParent();
        while (parent instanceof GrUnaryExpression)
          parent = parent.getParent();
        return parent.getNode().getElementType().equals(GroovyElementTypes.IF_STATEMENT);
      }

      private void checkParent(GrExpression expression) {
        PsiElement parent = expression.getParent();
        while (parent instanceof GrParenthesizedExpression)
          parent = parent.getParent();
        if (!(parent instanceof GrUnaryExpression)) return;

        final GrUnaryExpression prefixExpression = (GrUnaryExpression)parent;
        if (!GroovyTokenTypes.mLNOT.equals(prefixExpression.getOperationTokenType())) return;

        registerError(prefixExpression);
      }
    };
  }

}