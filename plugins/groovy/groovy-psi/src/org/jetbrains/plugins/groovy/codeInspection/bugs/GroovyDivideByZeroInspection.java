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

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyDivideByZeroInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Divide by zero";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Division by zero #loc";

  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
        @NotNull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final GrExpression rhs = expression.getRightOperand();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!GroovyTokenTypes.mDIV.equals(tokenType) &&
          !GroovyTokenTypes.mMOD.equals(tokenType)) {
        return;
      }
      if (!isZero(rhs)) {
        return;
      }
      registerError(expression);
    }

    @Override
    public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final GrExpression rhs = expression.getRValue();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(GroovyTokenTypes.mDIV_ASSIGN)
          && !tokenType.equals(GroovyTokenTypes.mMOD_ASSIGN)) {
        return;
      }
      if (!isZero(rhs)) {
        return;
      }
      registerError(expression);
    }


  }

  private static boolean isZero(GrExpression expression) {
    @NonNls
    final String text = expression.getText();
    return "0".equals(text) ||
        "0x0".equals(text) ||
        "0X0".equals(text) ||
        "0.0".equals(text) ||
        "0L".equals(text) ||
        "0l".equals(text);
  }
}