/*
 * Copyright 2008-2018 Bas Leijdekkers
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CharUsedInArithmeticContextInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("char.used.in.arithmetic.context.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("char.used.in.arithmetic.context.problem.descriptor");
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> result = new ArrayList<>();
    final PsiElement expression = (PsiElement)infos[0];
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpression) {
      final PsiExpression binaryExpression = (PsiExpression)parent;
      final PsiType type = binaryExpression.getType();
      if (type instanceof PsiPrimitiveType && !type.equals(PsiType.CHAR)) {
        final String typeText = type.getCanonicalText();
        result.add(new CharUsedInArithmeticContentCastFix(typeText));
      }
    }
    if (!(expression instanceof PsiLiteralExpression)) {
      return result.toArray(new InspectionGadgetsFix[result.size()]);
    }
    while (parent instanceof PsiPolyadicExpression) {
      if (ExpressionUtils.hasStringType((PsiExpression)parent)) {
        result.add(new CharUsedInArithmeticContentFix());
        break;
      }
      parent = parent.getParent();
    }

    return result.toArray(new InspectionGadgetsFix[result.size()]);
  }

  private static class CharUsedInArithmeticContentFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("char.used.in.arithmetic.context.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiLiteralExpression)) {
        return;
      }
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      final Object literal = literalExpression.getValue();
      if (!(literal instanceof Character)) {
        return;
      }
      final String escaped = StringUtil.escapeStringCharacters(literal.toString());
      PsiReplacementUtil.replaceExpression(literalExpression, '\"' + escaped + '"');
    }
  }

  private static class CharUsedInArithmeticContentCastFix extends InspectionGadgetsFix {

    private final String typeText;

    CharUsedInArithmeticContentCastFix(String typeText) {
      this.typeText = typeText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("char.used.in.arithmetic.context.cast.quickfix", typeText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Insert cast";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiExpression)) {
        return;
      }
      final PsiExpression expression = (PsiExpression)element;
      CommentTracker commentTracker = new CommentTracker();
      final String expressionText = commentTracker.text(expression);
      PsiReplacementUtil.replaceExpression(expression, '(' + typeText + ')' + expressionText, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CharUsedInArithmeticContextVisitor();
  }

  private static class CharUsedInArithmeticContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (ComparisonUtils.isComparisonOperation(tokenType)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      PsiType leftType = operands[0].getType();
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        final PsiType rightType = operand.getType();
        final PsiType expressionType = TypeConversionUtil.calcTypeForBinaryExpression(leftType, rightType, tokenType, true);
        if (TypeUtils.isJavaLangString(expressionType)) {
          return;
        }
        if (PsiType.CHAR.equals(rightType)) {
          registerError(operand, operand);
        }
        if (PsiType.CHAR.equals(leftType) && i == 1) {
          registerError(operands[0], operands[0]);
        }
        leftType = rightType;
      }
    }
  }
}
