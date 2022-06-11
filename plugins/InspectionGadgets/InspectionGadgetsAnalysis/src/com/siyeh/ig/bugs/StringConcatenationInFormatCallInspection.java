/*
 * Copyright 2010-2016 Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.FormatUtils;
import org.jetbrains.annotations.NotNull;

public class StringConcatenationInFormatCallInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.format.call.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInFormatCallVisitor();
  }

  private static class StringConcatenationInFormatCallVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (FormatUtils.isFormatCall(call)) {
        PsiExpressionList argumentList = call.getArgumentList();
        PsiExpression formatArgument = FormatUtils.getFormatArgument(argumentList);
        checkFormatString(call, formatArgument);
      }
      if (FormatUtils.STRING_FORMATTED.test(call)) {
        checkFormatString(call, call.getMethodExpression().getQualifierExpression());
      }
    }

    private void checkFormatString(PsiMethodCallExpression call, PsiExpression formatString) {
      formatString = PsiUtil.skipParenthesizedExprDown(formatString);
      if (!(formatString instanceof PsiPolyadicExpression)) return;
      if (!ExpressionUtils.hasStringType(formatString)) return;
      if (PsiUtil.isConstantExpression(formatString)) return;
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)formatString;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (!ContainerUtil.exists(operands, o -> ExpressionUtils.nonStructuralChildren(o).anyMatch(
        c -> c instanceof PsiReferenceExpression || c instanceof PsiMethodCallExpression || c instanceof PsiArrayAccessExpression))) {
        return;
      }
      registerError(formatString, call.getMethodExpression().getReferenceName());
    }
  }
}
