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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.FormatUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class StringConcatenationInFormatCallInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "string.concatenation.in.format.call.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "string.concatenation.in.format.call.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)infos[0];
    final String referenceName = referenceExpression.getReferenceName();
    return new StringConcatenationInFormatCallFix(referenceName);
  }

  private static class StringConcatenationInFormatCallFix
    extends InspectionGadgetsFix {

    private final String variableName;

    public StringConcatenationInFormatCallFix(String variableName) {
      this.variableName = variableName;
    }

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "string.concatenation.in.format.call.quickfix", variableName);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)element;
      final PsiElement parent = binaryExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      parent.add(rhs);
      parent.addAfter(lhs, binaryExpression);
      binaryExpression.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInFormatCallVisitor();
  }

  private static class StringConcatenationInFormatCallVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!FormatUtils.isFormatCall(expression)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      final PsiType type = firstArgument.getType();
      if (type == null) {
        return;
      }
      final int formatArgumentIndex;
      if ("java.util.Locale".equals(type.getCanonicalText())
          && arguments.length > 1) {
        formatArgumentIndex = 1;
      }
      else {
        formatArgumentIndex = 0;
      }
      final PsiExpression formatArgument = arguments[formatArgumentIndex];
      final PsiType formatArgumentType = formatArgument.getType();
      if (formatArgumentType == null ||
          !formatArgumentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      if (!(formatArgument instanceof PsiBinaryExpression)) {
        return;
      }
      if (PsiUtil.isConstantExpression(formatArgument)) {
        return;
      }

      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)formatArgument;
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiType lhsType = lhs.getType();
      if (lhsType == null || !lhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      final PsiExpression rhs = binaryExpression.getROperand();
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      registerError(formatArgument, rhs);
    }
  }
}
