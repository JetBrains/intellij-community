// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EqualityToEqualsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NewObjectEqualityInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "inspection.new.object.equality.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "inspection.new.object.equality.message");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NumberEqualityVisitor();
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return EqualityToEqualsFix.buildEqualityFixes((PsiBinaryExpression)infos[0]);
  }

  private static class NumberEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      final PsiExpression lhs = expression.getLOperand();
      if (isNewObject(rhs) || isNewObject(lhs)) {
        registerError(expression.getOperationSign(), expression);
      }
    }

    @Contract("null -> false")
    private static boolean isNewObject(PsiExpression expression) {
      expression = ExpressionUtils.resolveExpression(expression);
      if (expression instanceof PsiNewExpression) return true;
      if (expression instanceof PsiMethodCallExpression) {
        List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts((PsiCallExpression)expression);
        return ContractReturnValue.returnNew().equals(JavaMethodContractUtil.getNonFailingReturnValue(contracts));
      }
      return false;
    }
  }
}