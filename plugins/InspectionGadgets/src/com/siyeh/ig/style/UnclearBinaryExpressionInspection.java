/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class UnclearBinaryExpressionInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unclear.binary.expression.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unclear.binary.expression.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnclearBinaryExpressionFix();
  }

  private static class UnclearBinaryExpressionFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("unclear.binary.expression.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element;
      final StringBuilder newExpressionText = createReplacementText(polyadicExpression, new StringBuilder());
      replaceExpression(polyadicExpression, newExpressionText.toString());
    }

    private static StringBuilder createReplacementText(PsiExpression expression, StringBuilder out) {
      if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiPolyadicExpression) {
          final PsiPolyadicExpression parentPolyadicExpression = (PsiPolyadicExpression)parent;
          final IElementType parentOperationSign = parentPolyadicExpression.getOperationTokenType();
          if (!tokenType.equals(parentOperationSign)) {
            out.append('(');
            createText(polyadicExpression, out);
            out.append(')');
            return out;
          }
        } else if (parent instanceof PsiConditionalExpression || parent instanceof PsiInstanceOfExpression) {
          out.append('(');
          createText(polyadicExpression, out);
          out.append(')');
          return out;
        }
        createText(polyadicExpression, out);
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        final PsiExpression unwrappedExpression = parenthesizedExpression.getExpression();
        out.append('(');
        createReplacementText(unwrappedExpression, out);
        out.append(')');
      }
      else if (expression instanceof PsiInstanceOfExpression) {
        out.append('(');
        out.append(expression.getText());
        out.append(')');
      }
      else if (expression != null) {
        out.append(expression.getText());
      }
      return out;
    }

    private static void createText(PsiPolyadicExpression polyadicExpression, StringBuilder out) {
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand == null) {
          continue;
        }
        if (operand.getType() == PsiType.VOID) {
          throw new ProcessCanceledException();
        }
        if (operands.length == 1) {
          createReplacementText(operand, out);
        }
        final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
        if (token != null) {
          final PsiElement beforeToken = token.getPrevSibling();
          if (beforeToken instanceof PsiWhiteSpace) {
            out.append(beforeToken.getText());
          }
          out.append(token.getText());
          final PsiElement afterToken = token.getNextSibling();
          if (afterToken instanceof PsiWhiteSpace) {
            out.append(afterToken.getText());
          }
        }
        if (operands.length != 1) {
          createReplacementText(operand, out);
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnclearBinaryExpressionVisitor();
  }

  private static class UnclearBinaryExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiInstanceOfExpression ||
        parent instanceof PsiConditionalExpression) {
        registerError(expression);
        return;
      }
      if (parent instanceof PsiPolyadicExpression) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand instanceof PsiInstanceOfExpression) {
          registerError(expression);
          return;
        }
        if (!(operand instanceof PsiPolyadicExpression)) {
          continue;
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)operand;
        final IElementType childTokenType = polyadicExpression.getOperationTokenType();
        if (!tokenType.equals(childTokenType)) {
          registerError(expression);
          return;
        }
      }
      super.visitPolyadicExpression(expression);
    }
  }
}
