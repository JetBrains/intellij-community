/*
 * Copyright 2010 Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class DoubleLiteralMayBeFloatLiteralInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "double.literal.may.be.float.literal.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiTypeCastExpression typeCastExpression =
      (PsiTypeCastExpression)infos[0];
    final StringBuilder replacementText =
      buildReplacementText(typeCastExpression, new StringBuilder());
    return InspectionGadgetsBundle.message(
      "double.literal.may.be.float.literal.problem.descriptor",
      replacementText);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiTypeCastExpression typeCastExpression =
      (PsiTypeCastExpression)infos[0];
    final StringBuilder replacementText =
      buildReplacementText(typeCastExpression, new StringBuilder());
    return new DoubleLiteralMayBeFloatLiteralFix(replacementText.toString());
  }

  private static StringBuilder buildReplacementText(
    PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiLiteralExpression) {
      out.append(expression.getText());
      out.append('f');
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)expression;
      final PsiJavaToken sign = prefixExpression.getOperationSign();
      out.append(sign.getText());
      return buildReplacementText(prefixExpression.getOperand(), out);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)expression;
      out.append('(');
      buildReplacementText(parenthesizedExpression.getExpression(),
                           out);
      out.append(')');
    }
    else if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression =
        (PsiTypeCastExpression)expression;
      final PsiExpression operand = typeCastExpression.getOperand();
      buildReplacementText(operand, out);
    }
    else {
      assert false;
    }
    return out;
  }

  private static class DoubleLiteralMayBeFloatLiteralFix
    extends InspectionGadgetsFix {

    private final String replacementString;

    public DoubleLiteralMayBeFloatLiteralFix(String replacementString) {
      this.replacementString = replacementString;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "double.literal.may.be.float.literal.quickfix",
        replacementString);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with 'float'";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression =
        (PsiTypeCastExpression)element;
      PsiReplacementUtil.replaceExpression(typeCastExpression, replacementString);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DoubleLiteralMayBeFloatLiteralVisitor();
  }

  private static class DoubleLiteralMayBeFloatLiteralVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!PsiType.DOUBLE.equals(type)) {
        return;
      }
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiPrefixExpression ||
             parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression =
        (PsiTypeCastExpression)parent;
      final PsiType castType = typeCastExpression.getType();
      if (!PsiType.FLOAT.equals(castType)) {
        return;
      }
      registerError(typeCastExpression, typeCastExpression);
    }
  }
}
