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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
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
  static String calculateReplacementExpression(PsiLiteralExpression expression) {
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
    if (!(parent instanceof PsiBinaryExpression)) {
      if (parent instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
        final PsiExpression[] operands = polyadicExpression.getOperands();
        final PsiClassType stringType = PsiType.getJavaLangString(expression.getManager(), expression.getResolveScope());
        boolean seenString = false;
        boolean seenEmpty = false;
        final StringBuilder text = new StringBuilder();
        for (PsiExpression operand : operands) {
          operand = ParenthesesUtils.stripParentheses(operand);
          if (operand == null) {
            return null;
          }
          if (operand == expression) {
            seenEmpty = true;
            continue;
          }

          if (stringType.equals(operand.getType())) {
            seenString = true;
          }
          if (text.length() > 0) {
            text.append('+');
          }
          if (!seenString && seenEmpty) {
            text.append(buildReplacement(operand, seenString));
            seenString = true;
          }
          else {
            text.append(operand.getText());
          }
        }
        return text.toString();
      }
      return null;
    }
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
    return buildReplacement(replacement, false);
  }

  private static String buildReplacement(PsiExpression replacement, boolean seenString) {
    if (replacement == null) {
      return "";
    }
    if (ExpressionUtils.isNullLiteral(replacement)) {
      if (seenString) {
        return "(Object)null";
      }
      else {
        return "String.valueOf((Object)null)";
      }
    }
    if (seenString || TypeUtils.expressionHasType(replacement, CommonClassNames.JAVA_LANG_STRING)) {
      return replacement.getText();
    }
    return "String.valueOf(" + replacement.getText() + ')';
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryTemporaryObjectFix((PsiLiteralExpression)infos[0]);
  }

  private static class UnnecessaryTemporaryObjectFix extends InspectionGadgetsFix {

    private final String m_name;

    private UnnecessaryTemporaryObjectFix(PsiLiteralExpression expression) {
      m_name = InspectionGadgetsBundle.message("string.replace.quickfix", calculateReplacementExpression(expression));
    }

    @NotNull
    public String getName() {
      return m_name;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiLiteralExpression expression = (PsiLiteralExpression)descriptor.getPsiElement();
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (!(parent instanceof PsiExpression)) {
        return;
      }
      final String newExpression = calculateReplacementExpression(expression);
      replaceExpression((PsiExpression)parent, newExpression);
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
      if (!TypeUtils.expressionHasType(expression, CommonClassNames.JAVA_LANG_STRING)) {
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