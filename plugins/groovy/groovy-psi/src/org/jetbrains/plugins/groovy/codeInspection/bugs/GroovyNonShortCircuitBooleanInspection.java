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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyNonShortCircuitBooleanInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Non short-circuit boolean";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Non short-circuit boolean expression #loc";

  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public GroovyFix buildFix(@NotNull PsiElement location) {
    return new NonShortCircuitBooleanFix();
  }

  private static class NonShortCircuitBooleanFix
      extends GroovyFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return "Replace with short-circuit expression";
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final GrBinaryExpression expression =
          (GrBinaryExpression) descriptor.getPsiElement();
      final GrExpression lhs = expression.getLeftOperand();
      final GrExpression rhs = expression.getRightOperand();
      final IElementType operationSign = expression.getOperationTokenType();
      assert rhs != null;
      final String newExpression = lhs.getText() +
          getShortCircuitOperand(operationSign) + rhs.getText();
      replaceExpression(expression, newExpression);
    }

    private static String getShortCircuitOperand(IElementType tokenType) {
      if (tokenType.equals(GroovyTokenTypes.mBAND)) {
        return "&&";
      } else {
        return "||";
      }
    }
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final GrExpression rhs = expression.getRightOperand();
      if (rhs == null) {
        return;
      }
      final IElementType sign = expression.getOperationTokenType();
      if (!GroovyTokenTypes.mBAND.equals(sign) &&
          !GroovyTokenTypes.mBOR.equals(sign)) {
        return;
      }
      if (!PsiType.BOOLEAN.equals(rhs.getType())) {
        return;
      }
      registerError(expression);
    }
  }
}