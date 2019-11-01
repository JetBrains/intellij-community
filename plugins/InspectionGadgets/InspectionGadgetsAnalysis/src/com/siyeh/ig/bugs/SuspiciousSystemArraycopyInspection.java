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

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.SpecialFieldValue;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
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
      final PsiExpression dest = arguments[2];
      checkRanges(src, srcPos, dest, destPos, length);
      final PsiType srcType = src.getType();
      if (srcType == null) {
        return;
      }
      boolean notArrayReported = false;
      if (!(srcType instanceof PsiArrayType)) {
        registerError(src, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor4"));
        notArrayReported = true;
      }
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

    private void checkRanges(@NotNull PsiExpression src,
                             @NotNull PsiExpression srcPos,
                             @NotNull PsiExpression dest,
                             @NotNull PsiExpression destPos,
                             @NotNull PsiExpression length) {
      CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(src);
      if (result == null) return;
      SpecialFieldValue srcFact = result.getExpressionFact(src, DfaFactType.SPECIAL_FIELD_VALUE);
      if (srcFact == null) return;
      SpecialField srcLengthField = srcFact.getField();
      SpecialFieldValue destFact = result.getExpressionFact(dest, DfaFactType.SPECIAL_FIELD_VALUE);
      if (destFact == null) return;
      SpecialField destLengthField = destFact.getField();

      LongRangeSet srcLengthSet = DfaFactType.RANGE.fromDfaValue(srcLengthField.extract(srcFact));
      LongRangeSet destLengthSet = DfaFactType.RANGE.fromDfaValue(destLengthField.extract(destFact));
      LongRangeSet srcPosSet = result.getExpressionFact(srcPos, DfaFactType.RANGE);
      LongRangeSet destPosSet = result.getExpressionFact(destPos, DfaFactType.RANGE);
      LongRangeSet lengthSet = result.getExpressionFact(length, DfaFactType.RANGE);
      if (srcLengthSet == null || destLengthSet == null || srcPosSet == null || destPosSet == null || lengthSet == null) return;
      LongRangeSet srcPossibleLengthToCopy = srcLengthSet.minus(srcPosSet, false);
      LongRangeSet destPossibleLengthToCopy = srcLengthSet.minus(destPosSet, false);
      long lengthMin = lengthSet.min();
      if (lengthMin > destPossibleLengthToCopy.max()) {
        registerError(length, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor.length.bigger.dest", lengthSet.toString()));
      }
      else if (lengthMin > srcPossibleLengthToCopy.max()) {
        registerError(length, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor.length.bigger.src", lengthSet.toString()));
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