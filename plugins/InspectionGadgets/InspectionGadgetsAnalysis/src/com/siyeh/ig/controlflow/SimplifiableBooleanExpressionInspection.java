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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.util.ObjectUtils.tryCast;

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
    PsiPolyadicExpression conjunction =
      tryCast(PsiUtil.skipParenthesizedExprDown(expression.getLOperand()), PsiPolyadicExpression.class);
    if (conjunction == null) return null;
    final PsiExpression rightDisjunct = ParenthesesUtils.stripParentheses(expression.getROperand());
    if (rightDisjunct == null) return null;

    if (hasOperand(conjunction, rightDisjunct)) return commentTracker.text(rightDisjunct);
    PsiExpression[] operands = conjunction.getOperands();
    boolean isFirst;
    if (BoolUtils.areExpressionsOpposite(operands[0], rightDisjunct)) {
      isFirst = true;
    }
    else if (BoolUtils.areExpressionsOpposite(operands[operands.length - 1], rightDisjunct)) {
      isFirst = false;
    }
    else {
      return null;
    }
    String conjunctionRemnant;
    if (operands.length == 2) {
      conjunctionRemnant = commentTracker.text(operands[isFirst ? 1 : 0], ParenthesesUtils.OR_PRECEDENCE);
    }
    else {
      if (isFirst) {
        conjunctionRemnant = commentTracker.rangeText(operands[1], operands[operands.length - 1]);
      }
      else {
        conjunctionRemnant = commentTracker.rangeText(operands[0], operands[operands.length - 2]);
      }
      if (expression.getLOperand() instanceof PsiParenthesizedExpression) {
        conjunctionRemnant = "(" + conjunctionRemnant + ")";
      }
    }
    return isFirst ?
           commentTracker.text(rightDisjunct, ParenthesesUtils.OR_PRECEDENCE) + "||" + conjunctionRemnant :
           conjunctionRemnant + "||" + commentTracker.text(rightDisjunct, ParenthesesUtils.OR_PRECEDENCE);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableBooleanExpressionVisitor();
  }

  private static class SimplifiableBooleanExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!JavaTokenType.EXCL.equals(expression.getOperationTokenType())) return;

      PsiBinaryExpression maybeXor = tryCast(PsiUtil.skipParenthesizedExprDown(expression.getOperand()), PsiBinaryExpression.class);
      if (maybeXor == null || !JavaTokenType.XOR.equals(maybeXor.getOperationTokenType())) return;

      final PsiExpression lhs = ParenthesesUtils.stripParentheses(maybeXor.getLOperand());
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(maybeXor.getROperand());
      if (lhs == null || rhs == null) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitBinaryExpression(PsiBinaryExpression disjunction) {
      super.visitBinaryExpression(disjunction);
      if (!JavaTokenType.OROR.equals(disjunction.getOperationTokenType())) return;
      PsiPolyadicExpression conjunction =
        tryCast(PsiUtil.skipParenthesizedExprDown(disjunction.getLOperand()), PsiPolyadicExpression.class);
      if (conjunction == null || !JavaTokenType.ANDAND.equals(conjunction.getOperationTokenType())) return;

      final PsiExpression rightDisjunct = ParenthesesUtils.stripParentheses(disjunction.getROperand());
      if (hasOperand(conjunction, rightDisjunct) && !SideEffectChecker.mayHaveSideEffects(conjunction)) {
        registerError(disjunction, disjunction);
      }
      PsiExpression[] operands = conjunction.getOperands();
      if ((BoolUtils.areExpressionsOpposite(operands[0], rightDisjunct) ||
           BoolUtils.areExpressionsOpposite(operands[operands.length - 1], rightDisjunct)) &&
          !SideEffectChecker.mayHaveSideEffects(rightDisjunct)) {
        registerError(disjunction, disjunction);
      }
    }
  }

  private static boolean hasOperand(PsiPolyadicExpression polyadic, PsiExpression operand) {
    if (operand == null) return false;
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    return Arrays.stream(polyadic.getOperands()).anyMatch(op -> equivalence.expressionsAreEquivalent(op, operand));
  }
}
