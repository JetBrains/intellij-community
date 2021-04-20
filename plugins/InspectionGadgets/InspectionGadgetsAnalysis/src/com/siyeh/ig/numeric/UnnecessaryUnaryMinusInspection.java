/*
 * Copyright 2007-2018 Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.siyeh.ig.numeric.UnaryPlusInspection.*;

public final class UnnecessaryUnaryMinusInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new UnnecessaryUnaryMinusVisitor(holder, isOnTheFly);
  }

  private static class ReplaceParentOperatorFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.unary.minus.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)element.getParent();
      final PsiExpression parentExpression = (PsiExpression)prefixExpression.getParent();
      CommentTracker commentTracker = new CommentTracker();
      @NonNls final StringBuilder newExpression = new StringBuilder();
      if (parentExpression instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parentExpression;
        final PsiExpression lhs = assignmentExpression.getLExpression();
        newExpression.append(commentTracker.text(lhs));
        final IElementType tokenType = assignmentExpression.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.PLUSEQ)) {
          newExpression.append("-=");
        }
        else {
          newExpression.append("+=");
        }
      }
      else if (parentExpression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parentExpression;
        final PsiExpression lhs = binaryExpression.getLOperand();
        newExpression.append(commentTracker.text(lhs));
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.PLUS)) {
          newExpression.append('-');
        }
        else {
          newExpression.append('+');
        }
      }
      final PsiExpression operand = prefixExpression.getOperand();
      if (operand == null) {
        return;
      }

      newExpression.append(commentTracker.text(operand));
      PsiReplacementUtil.replaceExpression(parentExpression, newExpression.toString(), commentTracker);
    }
  }

  static class UnaryDecrementFix extends InspectionGadgetsFix {
    private final String myRefName;

    UnaryDecrementFix(@NotNull String refName) {
      myRefName = refName;
    }

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("unnecessary.unary.minus.decrement.quickfix", myRefName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.unary.minus.decrement.quickfix.family.name");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiPrefixExpression prefixExpr = ObjectUtils.tryCast(descriptor.getPsiElement().getParent(), PsiPrefixExpression.class);
      applyIncrementDecrementFix(prefixExpr, false);
    }
  }

  private static class RemoveDoubleUnaryMinusesFix extends InspectionGadgetsFix {
    private final boolean myMinusOnTheLeft;

    private RemoveDoubleUnaryMinusesFix(boolean minusOnTheLeft) {
      myMinusOnTheLeft = minusOnTheLeft;
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.unary.minus.remove.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiPrefixExpression prefixExpr = ObjectUtils.tryCast(descriptor.getPsiElement().getParent(), PsiPrefixExpression.class);
      if (prefixExpr == null) {
        return;
      }
      final PsiExpression oldExpr;
      final PsiExpression operand;
      if (myMinusOnTheLeft) {
        final PsiPrefixExpression child = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(prefixExpr.getOperand()),
                                                              PsiPrefixExpression.class);
        if (child == null) {
          return;
        }
        oldExpr = prefixExpr;
        operand = child.getOperand();
      }
      else {
        final PsiPrefixExpression parent = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(prefixExpr.getParent()),
                                                               PsiPrefixExpression.class);
        if (parent == null) {
          return;
        }
        oldExpr = parent;
        operand = prefixExpr.getOperand();
      }
      if (operand == null) {
        return;
      }
      PsiReplacementUtil.replaceExpression(oldExpr, operand.getText());
    }
  }

  private static class UnnecessaryUnaryMinusVisitor extends BaseInspectionVisitor {
    private final ProblemsHolder myProblemsHolder;
    private final boolean myOnTheFly;

    private UnnecessaryUnaryMinusVisitor(@NotNull ProblemsHolder problemsHolder, boolean onTheFly) {
      myProblemsHolder = problemsHolder;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression prefixExpr) {
      super.visitPrefixExpression(prefixExpr);
      if (!unaryMinusPrefixExpression(prefixExpr)) {
        return;
      }
      final PsiExpression operand = prefixExpr.getOperand();
      if (operand == null) {
        return;
      }
      final List<LocalQuickFix> fixes = new SmartList<>();
      addReplaceParentOperatorFix(fixes, prefixExpr);
      if (myOnTheFly) {
        final PsiElement parent = prefixExpr.getParent();
        if (operand instanceof PsiReferenceExpression) {
          final PsiPrefixExpression parentPrefixExpr = ObjectUtils.tryCast(parent, PsiPrefixExpression.class);
          if (unaryMinusPrefixExpression(parentPrefixExpr) &&
              containsOnlyWhitespaceBetweenOperatorAndOperand(prefixExpr) &&
              containsOnlyWhitespaceBetweenOperatorAndOperand(parentPrefixExpr)) {
            addIncrementDecrementFix(fixes, (PsiReferenceExpression)operand, false);
          }
        }
        else if (operand instanceof PsiPrefixExpression && unaryMinusPrefixExpression((PsiPrefixExpression)operand)) {
          final PsiExpression operandExpr = ((PsiPrefixExpression)operand).getOperand();
          final PsiReferenceExpression operandRefExpr = ObjectUtils.tryCast(operandExpr, PsiReferenceExpression.class);
          if (operandRefExpr != null &&
              containsOnlyWhitespaceBetweenOperatorAndOperand(prefixExpr) &&
              containsOnlyWhitespaceBetweenOperatorAndOperand((PsiPrefixExpression)operand)) {
            addIncrementDecrementFix(fixes, operandRefExpr, false);
          }
        }
        addRemoveDoubleUnaryMinusesFix(fixes, prefixExpr);
      }
      if (!fixes.isEmpty()) {
        myProblemsHolder.registerProblem(prefixExpr.getOperationSign(),
                                         InspectionGadgetsBundle.message("unnecessary.unary.minus.problem.descriptor"),
                                         ProblemHighlightType.LIKE_UNUSED_SYMBOL, fixes.toArray(LocalQuickFix[]::new));
      }
    }

    private static boolean unaryMinusPrefixExpression(@Nullable PsiPrefixExpression prefixExpr) {
      return prefixExpr != null && prefixExpr.getOperationTokenType().equals(JavaTokenType.MINUS);
    }

    private static void addReplaceParentOperatorFix(@NotNull List<LocalQuickFix> fixes, @NotNull PsiPrefixExpression prefixExpr) {
      final PsiElement parent = prefixExpr.getParent();
      if (parent instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
        if (ExpressionUtils.hasType(polyadicExpression, CommonClassNames.JAVA_LANG_STRING)) {
          return;
        }
        final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(prefixExpr);
        if (token == null) {
          return;
        }
        final IElementType binaryExpressionTokenType = token.getTokenType();
        if (!JavaTokenType.PLUS.equals(binaryExpressionTokenType)) {
          return;
        }
        fixes.add(new ReplaceParentOperatorFix());
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
        if (ExpressionUtils.hasType(assignmentExpression, CommonClassNames.JAVA_LANG_STRING)) {
          return;
        }
        final IElementType assignmentTokenType = assignmentExpression.getOperationTokenType();
        if (!JavaTokenType.PLUSEQ.equals(assignmentTokenType)) {
          return;
        }
        final PsiExpression rhs = assignmentExpression.getRExpression();
        if (!prefixExpr.equals(rhs)) {
          // don't warn on broken code.
          return;
        }
        fixes.add(new ReplaceParentOperatorFix());
      }
    }

    private static void addRemoveDoubleUnaryMinusesFix(@NotNull List<LocalQuickFix> fixes, @NotNull PsiPrefixExpression prefixExpr) {
      if (!containsOnlyWhitespaceBetweenOperatorAndOperand(prefixExpr)) {
        return;
      }
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(prefixExpr.getParent());
      if (containsCommentInParenthesisExpr(prefixExpr, parent)) {
        return;
      }
      final PsiExpression operandExpr;
      final PsiExpression expr;
      final boolean minusOnTheLeft;
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(prefixExpr.getOperand());
      if (operand == null) {
        return;
      }
      if (containsCommentInParenthesisExpr(operand, prefixExpr)) {
        return;
      }
      if (parent instanceof PsiPrefixExpression && unaryMinusPrefixExpression((PsiPrefixExpression)parent)) {
        if (!containsOnlyWhitespaceBetweenOperatorAndOperand((PsiPrefixExpression)parent)) {
          return;
        }
        operandExpr = prefixExpr.getOperand();
        expr = (PsiExpression)parent;
        minusOnTheLeft = false;
      }
      else if (operand instanceof PsiPrefixExpression && unaryMinusPrefixExpression((PsiPrefixExpression)operand)) {
        if (!containsOnlyWhitespaceBetweenOperatorAndOperand((PsiPrefixExpression)operand)) {
          return;
        }
        operandExpr = ((PsiPrefixExpression)operand).getOperand();
        expr = prefixExpr;
        minusOnTheLeft = true;
      }
      else {
        return;
      }
      if (operandExpr == null) {
        return;
      }
      final PsiType type = operandExpr.getType();
      if (TypeUtils.unaryNumericPromotion(type) != type && MethodCallUtils.isNecessaryForSurroundingMethodCall(expr, operandExpr)) {
        return;
      }
      fixes.add(new RemoveDoubleUnaryMinusesFix(minusOnTheLeft));
    }
  }

  private static boolean containsCommentInParenthesisExpr(@NotNull PsiElement from, @NotNull PsiElement to) {
    PsiElement parent = from.getParent();
    while (parent != to) {
      if (parent instanceof PsiParenthesizedExpression && PsiTreeUtil.findChildOfType(parent, PsiComment.class) != null) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }
}