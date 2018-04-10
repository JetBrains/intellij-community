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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TrivialStringConcatenationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "ConcatenationWithEmptyString";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("trivial.string.concatenation.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("trivial.string.concatenation.problem.descriptor");
  }

  @NonNls
  static String calculateReplacementExpression(PsiLiteralExpression expression, CommentTracker commentTracker) {
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
    if (!(parent instanceof PsiPolyadicExpression)) {
      return null;
    }
    if (parent instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
      final PsiExpression lOperand = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
      final PsiExpression rOperand = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
      final PsiExpression replacement;
      if (ExpressionUtils.isEmptyStringLiteral(lOperand)) {
        replacement = rOperand;
      }
      else {
        replacement = lOperand;
      }
      return replacement == null ? "" : buildReplacement(replacement, false, commentTracker);
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
    final PsiExpression[] operands = polyadicExpression.getOperands();
    final PsiClassType stringType = TypeUtils.getStringType(expression);
    boolean seenString = false;
    boolean seenEmpty = false;
    boolean replaced = false;
    PsiExpression operandToReplace = null;
    final StringBuilder text = new StringBuilder();
    for (PsiExpression operand : operands) {
      if (operandToReplace != null && !replaced) {
        if (ExpressionUtils.hasStringType(operand)) {
          seenString = true;
        }
        if (text.length() > 0) {
          text.append(" + ");
        }
        text.append(buildReplacement(operandToReplace, seenString, commentTracker));
        text.append(" + ");
        text.append(commentTracker.text(operand));
        replaced = true;
        continue;
      }
      if (ParenthesesUtils.stripParentheses(operand) == expression) {
        seenEmpty = true;
        continue;
      }
      if (seenEmpty && !replaced) {
        operandToReplace = operand;
        continue;
      }
      if (stringType.equals(operand.getType())) {
        seenString = true;
      }
      if (text.length() > 0) {
        text.append(" + ");
      }
      text.append(commentTracker.text(operand));
    }
    if (!replaced && operandToReplace != null) {
      text.append(" + ");
      text.append(buildReplacement(operandToReplace, seenString, commentTracker));
    }
    return text.toString();
  }

  @NonNls
  static String buildReplacement(@NotNull PsiExpression operandToReplace,
                                 boolean seenString,
                                 CommentTracker commentTracker) {
    if (ExpressionUtils.isNullLiteral(operandToReplace)) {
      if (seenString) {
        return "null";
      }
      else {
        return "String.valueOf((Object)null)";
      }
    }
    if (seenString || ExpressionUtils.hasStringType(operandToReplace)) {
      return operandToReplace.getText();
    }
    return "String.valueOf(" + commentTracker.text(operandToReplace) + ')';
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryTemporaryObjectFix((PsiLiteralExpression)infos[0]);
  }

  private static class UnnecessaryTemporaryObjectFix extends InspectionGadgetsFix {

    private final String m_name;

    UnnecessaryTemporaryObjectFix(PsiLiteralExpression expression) {
      m_name = InspectionGadgetsBundle.message("string.replace.quickfix", calculateReplacementExpression(expression, new CommentTracker()));
    }

    @Override
    @NotNull
    public String getName() {
      return m_name;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace concatenation";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiLiteralExpression expression = (PsiLiteralExpression)descriptor.getPsiElement();
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (!(parent instanceof PsiExpression)) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final String newExpression = calculateReplacementExpression(expression, commentTracker);
      PsiReplacementUtil.replaceExpression((PsiExpression)parent, newExpression, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TrivialStringConcatenationVisitor();
  }

  private static class TrivialStringConcatenationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      if (!ExpressionUtils.hasStringType(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        operand = ParenthesesUtils.stripParentheses(operand);
        if (operand == null) {
          return;
        }
        if (!ExpressionUtils.isEmptyStringLiteral(operand)) {
          continue;
        }
        if (PsiUtil.isConstantExpression(expression)) {
          return;
        }
        registerError(operand, operand);
      }
    }
  }
}