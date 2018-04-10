/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SimplifiableBooleanExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("simplifiable.boolean.expression.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)info;
      return InspectionGadgetsBundle.message("boolean.expression.can.be.simplified.problem.descriptor",
                                             calculateReplacementExpression(prefixExpression, new CommentTracker()));
    }
    else {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)info;
      return InspectionGadgetsBundle.message("boolean.expression.can.be.simplified.problem.descriptor",
                                             calculateReplacementExpression(binaryExpression, new CommentTracker()));
    }
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SimplifiableBooleanExpressionFix();
  }

  private static class SimplifiableBooleanExpressionFix extends InspectionGadgetsFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      CommentTracker commentTracker = new CommentTracker();
      final String replacement;
      if (element instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)element;
        replacement = calculateReplacementExpression(prefixExpression, commentTracker);
      }
      else if (element instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
        replacement = calculateReplacementExpression(binaryExpression, commentTracker);
      }
      else {
        return;
      }
      if (replacement == null) {
        return;
      }

      PsiReplacementUtil.replaceExpression((PsiExpression)element, replacement, commentTracker);
    }
  }

  @NonNls
  static String calculateReplacementExpression(PsiPrefixExpression expression, CommentTracker commentTracker) {
    final PsiExpression operand = ParenthesesUtils.stripParentheses(expression.getOperand());
    if (!(operand instanceof PsiBinaryExpression)) {
      return null;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)operand;
    final PsiExpression lhs = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
    final PsiExpression rhs = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
    if (lhs == null || rhs == null) {
      return null;
    }
    return ParenthesesUtils.getText(commentTracker.markUnchanged(lhs), ParenthesesUtils.EQUALITY_PRECEDENCE) + "==" +
           ParenthesesUtils.getText(commentTracker.markUnchanged(rhs), ParenthesesUtils.EQUALITY_PRECEDENCE);
  }

  @NonNls
  static String calculateReplacementExpression(PsiBinaryExpression expression, CommentTracker commentTracker) {
    final PsiExpression rhs1 = ParenthesesUtils.stripParentheses(expression.getROperand());
    if (rhs1 == null) {
      return null;
    }
    final PsiExpression lhs = ParenthesesUtils.stripParentheses(expression.getLOperand());
    if (!(lhs instanceof PsiBinaryExpression)) {
      return null;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)lhs;
    final PsiExpression rhs2 = binaryExpression.getROperand();
    if (rhs2 == null) {
      return null;
    }
    return ParenthesesUtils.getText(commentTracker.markUnchanged(rhs1), ParenthesesUtils.OR_PRECEDENCE) + "||" +
           ParenthesesUtils.getText(commentTracker.markUnchanged(rhs2), ParenthesesUtils.OR_PRECEDENCE);

  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableBooleanExpressionVisitor();
  }

  private static class SimplifiableBooleanExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.EXCL.equals(tokenType)) {
        return;
      }
      final PsiExpression operand = ParenthesesUtils.stripParentheses(expression.getOperand());
      if (!(operand instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)operand;
      final IElementType binaryTokenType = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.XOR.equals(binaryTokenType)) {
        return;
      }
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
      if (lhs == null || rhs == null) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType1 = expression.getOperationTokenType();
      if (!JavaTokenType.OROR.equals(tokenType1)) {
        return;
      }
      final PsiExpression lhs1 = ParenthesesUtils.stripParentheses(expression.getLOperand());
      if (!(lhs1 instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)lhs1;
      final IElementType tokenType2 = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.ANDAND.equals(tokenType2)) {
        return;
      }
      final PsiExpression lhs2 = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
      final PsiExpression rhs1 = ParenthesesUtils.stripParentheses(expression.getROperand());
      final PsiExpression negated = BoolUtils.getNegated(rhs1);
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(lhs2, negated)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}
