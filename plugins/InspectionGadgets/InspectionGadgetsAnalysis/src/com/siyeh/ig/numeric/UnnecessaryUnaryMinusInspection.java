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

import java.util.List;

import static com.siyeh.ig.numeric.UnaryPlusInspection.ConvertDoubleUnaryToPrefixOperationFix;

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
      final PsiExpression operand = prefixExpression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiExpression parentExpression = (PsiExpression)prefixExpression.getParent();
      final CommentTracker commentTracker = new CommentTracker();
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
        newExpression.append(commentTracker.text(operand));
      }
      else if (parentExpression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parentExpression;
        int lastOperatorIndex = -1;
        IElementType lastOperator = null;
        for (PsiElement child = polyadicExpression.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (child == prefixExpression) {
            if (lastOperatorIndex == -1) {
              return;
            }
            newExpression.replace(lastOperatorIndex, lastOperatorIndex + 1, lastOperator == JavaTokenType.PLUS ? "-" : "+");
            newExpression.append(commentTracker.text(operand));
            continue;
          }
          if (PsiUtil.isJavaToken(child, JavaTokenType.PLUS)) {
            lastOperatorIndex = newExpression.length();
            lastOperator = JavaTokenType.PLUS;
          }
          else if (PsiUtil.isJavaToken(child, JavaTokenType.MINUS)) {
            lastOperatorIndex = newExpression.length();
            lastOperator = JavaTokenType.MINUS;
          }
          newExpression.append(commentTracker.text(child));
        }
        if (lastOperatorIndex == -1) {
          return;
        }
      }
      PsiReplacementUtil.replaceExpression(parentExpression, newExpression.toString(), commentTracker);
    }
  }

  private static class RemoveDoubleUnaryMinusFix extends InspectionGadgetsFix {
    private final boolean myMinusOnTheLeft;

    private RemoveDoubleUnaryMinusFix(boolean minusOnTheLeft) {
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
      if (!ConvertDoubleUnaryToPrefixOperationFix.isDesiredPrefixExpression(prefixExpr, false)) {
        return;
      }
      final PsiExpression operand = prefixExpr.getOperand();
      if (operand == null) {
        return;
      }
      final List<LocalQuickFix> fixes = new SmartList<>();
      addReplaceParentOperatorFix(fixes, prefixExpr);
      if (myOnTheFly) {
        LocalQuickFix decrementFix = ConvertDoubleUnaryToPrefixOperationFix.createFix(prefixExpr);
        if (decrementFix != null) {
          fixes.add(decrementFix);
        }
        addRemoveDoubleUnaryMinusFix(fixes, prefixExpr);
      }
      if (!fixes.isEmpty()) {
        myProblemsHolder.registerProblem(prefixExpr.getOperationSign(),
                                         InspectionGadgetsBundle.message("unnecessary.unary.minus.problem.descriptor"),
                                         ProblemHighlightType.LIKE_UNUSED_SYMBOL, fixes.toArray(LocalQuickFix[]::new));
      }
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
        final IElementType tokenType = token.getTokenType();
        if (!JavaTokenType.PLUS.equals(tokenType) && !JavaTokenType.MINUS.equals(tokenType)) {
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

    private static void addRemoveDoubleUnaryMinusFix(@NotNull List<LocalQuickFix> fixes,
                                                     @NotNull PsiPrefixExpression prefixExpr) {
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(prefixExpr.getParent());
      final PsiExpression operandExpr;
      final PsiExpression expr;
      final boolean minusOnTheLeft;
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(prefixExpr.getOperand());
      if (operand == null) {
        return;
      }
      if (parent instanceof PsiPrefixExpression &&
          ConvertDoubleUnaryToPrefixOperationFix.isDesiredPrefixExpression((PsiPrefixExpression)parent, false)) {
        operandExpr = prefixExpr.getOperand();
        expr = (PsiExpression)parent;
        minusOnTheLeft = false;
      }
      else if (operand instanceof PsiPrefixExpression &&
               ConvertDoubleUnaryToPrefixOperationFix.isDesiredPrefixExpression((PsiPrefixExpression)operand, false)) {
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
      fixes.add(new RemoveDoubleUnaryMinusFix(minusOnTheLeft));
    }
  }
}