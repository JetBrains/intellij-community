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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public final class GroovyNonShortCircuitBooleanInspection extends BaseInspection {

  @Override
  protected @Nullable String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.non.short.circuit.boolean.expression");

  }

  @Override
  public LocalQuickFix buildFix(@NotNull PsiElement location) {
    return new NonShortCircuitBooleanFix();
  }

  private static class NonShortCircuitBooleanFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("intention.family.name.replace.with.short.circuit.expression");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final GrBinaryExpression expression = (GrBinaryExpression) element;
      final GrExpression lhs = expression.getLeftOperand();
      final GrExpression rhs = expression.getRightOperand();
      final IElementType operationSign = expression.getOperationTokenType();
      assert rhs != null;
      final String newExpression = lhs.getText() + getShortCircuitOperand(operationSign) + rhs.getText();
      GrInspectionUtil.replaceExpression(expression, newExpression);
    }

    private static String getShortCircuitOperand(IElementType tokenType) {
      if (tokenType.equals(GroovyTokenTypes.mBAND)) {
        return "&&";
      } else {
        return "||";
      }
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
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
      if (!PsiTypes.booleanType().equals(rhs.getType())) {
        return;
      }
      registerError(expression);
    }
  }
}