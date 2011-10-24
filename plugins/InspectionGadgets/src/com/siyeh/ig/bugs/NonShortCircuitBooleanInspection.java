/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class NonShortCircuitBooleanInspection extends BaseInspection {

  @NotNull
  public String getID() {
    return "NonShortCircuitBooleanExpression";
  }

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.display.name");
  }

  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.problem.descriptor");
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new NonShortCircuitBooleanFix();
  }

  private static class NonShortCircuitBooleanFix
    extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiPolyadicExpression expression = (PsiPolyadicExpression)descriptor.getPsiElement();
      final IElementType tokenType = expression.getOperationTokenType();
      final String operandText = getShortCircuitOperand(tokenType);
      final PsiExpression[] operands = expression.getOperands();
      final StringBuilder newExpression = new StringBuilder();
      for (PsiExpression operand : operands) {
        if (newExpression.length() != 0) {
          newExpression.append(operandText);
        }
        newExpression.append(operand.getText());
      }
      replaceExpression(expression, newExpression.toString());
    }

    private static String getShortCircuitOperand(IElementType tokenType) {
      if (tokenType.equals(JavaTokenType.AND)) {
        return "&&";
      }
      else {
        return "||";
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NonShortCircuitBooleanVisitor();
  }

  private static class NonShortCircuitBooleanVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.AND) && !tokenType.equals(JavaTokenType.OR)) {
        return;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!type.equals(PsiType.BOOLEAN)) {
        return;
      }
      registerError(expression);
    }
  }
}