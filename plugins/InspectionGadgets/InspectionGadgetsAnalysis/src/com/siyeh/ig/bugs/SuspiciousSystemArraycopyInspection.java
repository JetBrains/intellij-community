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
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

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
      final PsiExpression destPos = arguments[3];
      final PsiExpression length = arguments[4];
      final PsiExpression src = arguments[0];
      final PsiExpression dest = arguments[2];
      checkRanges(src, srcPos, dest, destPos, length, expression);
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
                             @NotNull PsiExpression length,
                             @NotNull PsiMethodCallExpression call) {
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
      LongRangeSet destPossibleLengthToCopy = destLengthSet.minus(destPosSet, false);
      long lengthMin = lengthSet.min();
      if (lengthMin > destPossibleLengthToCopy.max()) {
        registerError(length, InspectionGadgetsBundle
          .message("suspicious.system.arraycopy.problem.descriptor.length.bigger.dest", lengthSet.toString()));
        return;
      }
      if (lengthMin > srcPossibleLengthToCopy.max()) {
        registerError(length, InspectionGadgetsBundle
          .message("suspicious.system.arraycopy.problem.descriptor.length.bigger.src", lengthSet.toString()));
        return;
      }

      if (!isTheSameArray(src, dest)) return;
      LongRangeSet srcRange = getDefiniteRange(srcPosSet, lengthSet);
      LongRangeSet destRange = getDefiniteRange(destPosSet, lengthSet);
      if (srcRange == null || destRange == null) return;
      if (srcRange.intersects(destRange)) {
        PsiElement name = call.getMethodExpression().getReferenceNameElement();
        PsiElement elementToHighlight = name == null ? call : name;
        registerError(elementToHighlight,
                      InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor.ranges.intersect"));
      }
    }

    private static LongRangeSet getDefiniteRange(@NotNull LongRangeSet startSet, @NotNull LongRangeSet lengthSet) {
      long maxLeftBorder = startSet.max();
      LongRangeSet lengthMinusOne = lengthSet.minus(LongRangeSet.point(1), false);
      long minRightBorder = startSet.plus(lengthMinusOne, false).min();
      if (maxLeftBorder > minRightBorder) return null;
      return LongRangeSet.range(maxLeftBorder, minRightBorder);
    }

    private static boolean isTheSameArray(@NotNull PsiExpression src,
                                          @NotNull PsiExpression dest) {
      PsiReferenceExpression srcReference = tryCast(ParenthesesUtils.stripParentheses(src), PsiReferenceExpression.class);
      PsiReferenceExpression destReference = tryCast(ParenthesesUtils.stripParentheses(dest), PsiReferenceExpression.class);
      if (srcReference == null || destReference == null) return false;
      PsiElement srcVariable = srcReference.resolve();
      PsiElement destVariable = destReference.resolve();
      if (srcVariable == null || srcVariable != destVariable) return false;
      return true;
    }
  }
}