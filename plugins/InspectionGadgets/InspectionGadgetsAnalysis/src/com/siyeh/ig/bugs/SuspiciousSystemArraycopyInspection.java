/*
 * Copyright 2005-2017 Bas Leijdekkers
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
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class SuspiciousSystemArraycopyInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("suspicious.system.arraycopy.display.name");
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

  private static class SuspiciousSystemArraycopyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiClassType objectType = TypeUtils.getObjectType(expression);
      if (!MethodCallUtils.isCallToMethod(expression, "java.lang.System", PsiType.VOID, "arraycopy",
                                          objectType, PsiType.INT, objectType, PsiType.INT, PsiType.INT)) {
        return;
      }
      final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      if (arguments.length != 5) {
        return;
      }
      final PsiExpression srcPos = arguments[1];
      if (isNegativeArgument(srcPos)) {
        registerError(srcPos, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor1"));
      }
      final PsiExpression destPos = arguments[3];
      if (isNegativeArgument(destPos)) {
        registerError(destPos, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor2"));
      }
      final PsiExpression length = arguments[4];
      if (isNegativeArgument(length)) {
        registerError(length, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor3"));
      }
      final PsiExpression src = arguments[0];
      final PsiType srcType = src.getType();
      if (srcType == null) {
        return;
      }
      boolean notArrayReported = false;
      if (!(srcType instanceof PsiArrayType)) {
        registerError(src, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor4"));
        notArrayReported = true;
      }
      final PsiExpression dest = arguments[2];
      final PsiType destType = dest.getType();
      if (destType == null) {
        return;
      }
      if (!(destType instanceof PsiArrayType)) {
        registerError(dest, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor5"));
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
          registerError(dest, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor6",
                                                              srcType.getCanonicalText(),
                                                              destType.getCanonicalText()));
        }
      }
      else if (!destComponentType.equals(srcComponentType)) {
        registerError(dest, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor6",
                                                            srcType.getCanonicalText(),
                                                            destType.getCanonicalText()));
      }
    }

    private static boolean isNegativeArgument(@NotNull PsiExpression argument) {
      final Object constant = ExpressionUtils.computeConstantExpression(argument);
      if (!(constant instanceof Integer)) {
        return false;
      }
      final Integer integer = (Integer)constant;
      return integer.intValue() < 0;
    }
  }
}