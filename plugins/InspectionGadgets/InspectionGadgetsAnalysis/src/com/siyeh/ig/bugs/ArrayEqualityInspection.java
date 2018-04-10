/*
 * Copyright 2011-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ArrayEqualityInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "array.comparison.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "array.comparison.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiArrayType type = (PsiArrayType)infos[0];
    final PsiType componentType = type.getComponentType();
    if (componentType instanceof PsiArrayType) {
      return new ArrayEqualityFix(true);
    }
    return new ArrayEqualityFix(false);
  }

  private static class ArrayEqualityFix extends InspectionGadgetsFix {

    private final boolean deepEquals;

    public ArrayEqualityFix(boolean deepEquals) {
      this.deepEquals = deepEquals;
    }

    @NotNull
    @Override
    public String getName() {
      if (deepEquals) {
        return InspectionGadgetsBundle.message(
          "replace.with.arrays.deep.equals");
      }
      else {
        return InspectionGadgetsBundle.message(
          "replace.with.arrays.equals");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.arrays.equals");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiBinaryExpression)) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      @NonNls final StringBuilder newExpressionText = new StringBuilder();
      if (JavaTokenType.NE.equals(tokenType)) {
        newExpressionText.append('!');
      }
      else if (!JavaTokenType.EQEQ.equals(tokenType)) {
        return;
      }
      if (deepEquals) {
        newExpressionText.append("java.util.Arrays.deepEquals(");
      }
      else {
        newExpressionText.append("java.util.Arrays.equals(");
      }
      newExpressionText.append(commentTracker.text(binaryExpression.getLOperand()));
      newExpressionText.append(',');
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      newExpressionText.append(commentTracker.text(rhs));
      newExpressionText.append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(binaryExpression, newExpressionText.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayEqualityVisitor();
  }

  private static class ArrayEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null || !ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiType lhsType = lhs.getType();
      if (!(lhsType instanceof PsiArrayType) || !(rhs.getType() instanceof PsiArrayType)) {
        return;
      }
      registerError(expression.getOperationSign(), lhsType);
    }
  }
}