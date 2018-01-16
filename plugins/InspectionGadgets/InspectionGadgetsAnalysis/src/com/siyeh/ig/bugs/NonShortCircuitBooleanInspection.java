/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class NonShortCircuitBooleanInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "NonShortCircuitBooleanExpression";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new NonShortCircuitBooleanFix();
  }

  private static class NonShortCircuitBooleanFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiPolyadicExpression expression = (PsiPolyadicExpression)descriptor.getPsiElement();
      final IElementType tokenType = expression.getOperationTokenType();
      final String operandText = getShortCircuitOperand(tokenType);
      final PsiExpression[] operands = expression.getOperands();
      final StringBuilder newExpression = new StringBuilder();
      CommentTracker commentTracker = new CommentTracker();
      for (PsiExpression operand : operands) {
        if (newExpression.length() != 0) {
          newExpression.append(operandText);
        }
        newExpression.append(commentTracker.text(operand));
      }
      PsiReplacementUtil.replaceExpression(expression, newExpression.toString(), commentTracker);
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

  @Override
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