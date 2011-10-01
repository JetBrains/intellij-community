/*
 * Copyright 2005-2009 Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SuspiciousSystemArraycopyInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "suspicious.system.arraycopy.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousSystemArraycopyVisitor();
  }

  private static class SuspiciousSystemArraycopyVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!"arraycopy".equals(name)) {
        return;
      }
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifierExpression;
      final String canonicalText = referenceExpression.getCanonicalText();
      if (!canonicalText.equals("java.lang.System")) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 5) {
        return;
      }
      final PsiExpression src = arguments[0];
      final PsiType srcType = src.getType();
      final PsiExpression srcPos = arguments[1];
      if (isNegativeArgument(srcPos)) {
        final String errorString = InspectionGadgetsBundle.message(
          "suspicious.system.arraycopy.problem.descriptor1");
        registerError(srcPos, errorString);
      }
      final PsiExpression destPos = arguments[3];
      if (isNegativeArgument(destPos)) {
        final String errorString = InspectionGadgetsBundle.message(
          "suspicious.system.arraycopy.problem.descriptor2");
        registerError(destPos, errorString);
      }
      final PsiExpression length = arguments[4];
      if (isNegativeArgument(length)) {
        final String errorString = InspectionGadgetsBundle.message(
          "suspicious.system.arraycopy.problem.descriptor3");
        registerError(length, errorString);
      }
      boolean notArrayReported = false;
      if (!(srcType instanceof PsiArrayType)) {
        final String errorString = InspectionGadgetsBundle.message(
          "suspicious.system.arraycopy.problem.descriptor4");
        registerError(src, errorString);
        notArrayReported = true;
      }
      final PsiExpression dest = arguments[2];
      final PsiType destType = dest.getType();
      if (!(destType instanceof PsiArrayType)) {
        final String errorString = InspectionGadgetsBundle.message(
          "suspicious.system.arraycopy.problem.descriptor5");
        registerError(dest, errorString);
        notArrayReported = true;
      }
      if (notArrayReported) {
        return;
      }
      final PsiArrayType srcArrayType = (PsiArrayType)srcType;
      final PsiArrayType destArrayType = (PsiArrayType)destType;
      final PsiType srcComponentType = srcArrayType.getComponentType();
      final PsiType destComponentType = destArrayType.getComponentType();
      if (!(srcComponentType instanceof PsiPrimitiveType)) {
        if (!destComponentType.isAssignableFrom(srcComponentType)) {
          final String errorString = InspectionGadgetsBundle.message(
            "suspicious.system.arraycopy.problem.descriptor6",
            srcType.getCanonicalText(),
            destType.getCanonicalText());
          registerError(dest, errorString);
        }
      }
      else if (!destComponentType.equals(srcComponentType)) {
        final String errorString = InspectionGadgetsBundle.message(
          "suspicious.system.arraycopy.problem.descriptor6",
          srcType.getCanonicalText(),
          destType.getCanonicalText());
        registerError(dest, errorString);
      }
    }

    private static boolean isNegativeArgument(
      @NotNull PsiExpression argument) {
      final Object constant =
        ExpressionUtils.computeConstantExpression(argument);
      if (!(constant instanceof Integer)) {
        return false;
      }
      final Integer integer = (Integer)constant;
      return integer.intValue() < 0;
    }
  }
}