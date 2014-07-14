/*
 * Copyright 2010-2012 Bas Leijdekkers
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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.FormatUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class StringConcatenationInFormatCallInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.in.format.call.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.format.call.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new StringConcatenationInFormatCallFix(((Boolean)infos[0]).booleanValue());
  }

  private static class StringConcatenationInFormatCallFix extends InspectionGadgetsFix {


    private final boolean myPlural;

    public StringConcatenationInFormatCallFix(boolean plural) {
      myPlural = plural;
    }

    @Override
    @NotNull
    public String getName() {
      if (myPlural) {
        return InspectionGadgetsBundle.message("string.concatenation.in.format.call.plural.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message("string.concatenation.in.format.call.quickfix");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.in.format.call.plural.quickfix"); 
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement().getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression formatArgument = FormatUtils.getFormatArgument(argumentList);
      if (!(formatArgument instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)formatArgument;
      final StringBuilder newExpression = new StringBuilder();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand instanceof PsiReferenceExpression) {
          argumentList.add(operand);
          continue;
        }
        final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
        if (token != null) {
          newExpression.append(token.getText());
        }
        newExpression.append(operand.getText());
      }
      PsiReplacementUtil.replaceExpression(polyadicExpression, newExpression.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInFormatCallVisitor();
  }

  private static class StringConcatenationInFormatCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!FormatUtils.isFormatCall(expression)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression formatArgument = FormatUtils.getFormatArgument(argumentList);
      if (!ExpressionUtils.hasStringType(formatArgument)) {
        return;
      }
      if (!(formatArgument instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)formatArgument;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      int count = 0;
      for (final PsiExpression operand : operands) {
        if (!(operand instanceof PsiReferenceExpression)) {
          continue;
        }
        count++;
        if (count > 1) {
          break;
        }
      }
      if (count == 0) {
        return;
      }
      registerMethodCallError(expression, Boolean.valueOf(count > 1));
    }
  }
}
