/*
 * Copyright 2006-2017 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.numeric.UnnecessaryUnaryMinusInspection.UnaryDecrementFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public final class UnaryPlusInspection extends LocalInspectionTool {
  public boolean onlyReportInsideBinaryExpression = true;

  @NotNull
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaAnalysisBundle.message("inspection.unary.plus.unary.binary.option"), this,
                                          "onlyReportInsideBinaryExpression");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!onlyReportInsideBinaryExpression) {
      node.addContent(new Element("option").setAttribute("name", "onlyReportInsideBinaryExpression").setAttribute("value", "false"));
    }
  }

  private static class UnaryPlusFix extends InspectionGadgetsFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unary.plus.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiPrefixExpression)) {
        return;
      }
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
      final PsiExpression operand = prefixExpression.getOperand();
      if (operand == null) {
        return;
      }
      prefixExpression.replace(operand);
    }
  }

  private static class UnaryIncrementFix extends InspectionGadgetsFix {
    private final String myRefName;

    private UnaryIncrementFix(@NotNull String refName) {
      myRefName = refName;
    }

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("unary.increment.quickfix", myRefName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("unary.increment.quickfix.family.name");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiPrefixExpression prefixExpr = ObjectUtils.tryCast(descriptor.getPsiElement().getParent(), PsiPrefixExpression.class);
      if (prefixExpr != null) {
        IncrementDecrementFixManager.increment(prefixExpr).applyFix();
      }
    }
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new UnaryPlusVisitor(holder, isOnTheFly, onlyReportInsideBinaryExpression);
  }

  private static class UnaryPlusVisitor extends BaseInspectionVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myOnTheFly;
    private final boolean myOnlyReportInsideBinaryExpression;

    private UnaryPlusVisitor(@NotNull ProblemsHolder holder, boolean onTheFly, boolean onlyReportInsideBinaryExpression) {
      myHolder = holder;
      myOnTheFly = onTheFly;
      myOnlyReportInsideBinaryExpression = onlyReportInsideBinaryExpression;
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression prefixExpression) {
      super.visitPrefixExpression(prefixExpression);
      IncrementDecrementFixManager fixManager = IncrementDecrementFixManager.increment(prefixExpression);
      if (!fixManager.isValidPrefixExpression(prefixExpression)) {
        return;
      }
      final PsiExpression operand = prefixExpression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType type = operand.getType();
      if (type == null) {
        return;
      }
      if (myOnlyReportInsideBinaryExpression) {
        final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(prefixExpression);
        if (!(operand instanceof PsiParenthesizedExpression ||
              operand instanceof PsiPrefixExpression ||
              parent instanceof PsiPolyadicExpression ||
              parent instanceof PsiPrefixExpression ||
              parent instanceof PsiAssignmentExpression ||
              parent instanceof PsiVariable)) {
          return;
        }
      }
      else if (TypeUtils.unaryNumericPromotion(type) != type &&
               MethodCallUtils.isNecessaryForSurroundingMethodCall(prefixExpression, operand)) {
        // unary plus might have been used as cast to int
        return;
      }
      List<LocalQuickFix> fixes = new SmartList<>(new UnaryPlusFix());
      if (myOnTheFly) {
        LocalQuickFix incrementFix = fixManager.createFix();
        if (incrementFix != null) {
          fixes.add(incrementFix);
        }
      }
      myHolder.registerProblem(prefixExpression.getOperationSign(), InspectionGadgetsBundle.message("unary.plus.problem.descriptor"),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL, fixes.toArray(LocalQuickFix[]::new));
    }
  }

  static abstract class IncrementDecrementFixManager {
    private final PsiPrefixExpression myPrefixExpr;
    private final String myOperatorText;

    private IncrementDecrementFixManager(@NotNull PsiPrefixExpression prefixExpr, @NotNull String operatorText) {
      myPrefixExpr = prefixExpr;
      myOperatorText = operatorText;
    }

    @NotNull
    static IncrementDecrementFixManager increment(@NotNull PsiPrefixExpression prefixExpr) {
      return new IncrementFixManager(prefixExpr);
    }

    @NotNull
    static IncrementDecrementFixManager decrement(@NotNull PsiPrefixExpression prefixExpr) {
      return new DecrementFixManager(prefixExpr);
    }

    @Nullable
    LocalQuickFix createFix() {
      final PsiExpression operand = myPrefixExpr.getOperand();
      if (operand instanceof PsiReferenceExpression) {
        final PsiPrefixExpression parentPrefixExpr = ObjectUtils.tryCast(myPrefixExpr.getParent(), PsiPrefixExpression.class);
        if (isValidPrefixExpression(parentPrefixExpr) &&
            containsOnlyWhitespaceBetweenOperatorAndOperand(myPrefixExpr) &&
            containsOnlyWhitespaceBetweenOperatorAndOperand(parentPrefixExpr)) {
          return createFix((PsiReferenceExpression)operand);
        }
      }
      else if (operand instanceof PsiPrefixExpression && isValidPrefixExpression((PsiPrefixExpression)operand)) {
        final PsiExpression operandExpr = ((PsiPrefixExpression)operand).getOperand();
        final PsiReferenceExpression operandRefExpr = ObjectUtils.tryCast(operandExpr, PsiReferenceExpression.class);
        if (operandRefExpr != null &&
            containsOnlyWhitespaceBetweenOperatorAndOperand(myPrefixExpr) &&
            containsOnlyWhitespaceBetweenOperatorAndOperand((PsiPrefixExpression)operand)) {
          return createFix(operandRefExpr);
        }
      }
      return null;
    }

    void applyFix() {
      final PsiExpression operand = myPrefixExpr.getOperand();
      final PsiExpression oldExpr;
      final PsiReferenceExpression refExpr;
      if (operand instanceof PsiReferenceExpression) {
        oldExpr = (PsiExpression)myPrefixExpr.getParent();
        refExpr = (PsiReferenceExpression)operand;
      }
      else if (operand instanceof PsiPrefixExpression) {
        oldExpr = myPrefixExpr;
        refExpr = (PsiReferenceExpression)((PsiPrefixExpression)operand).getOperand();
      }
      else {
        return;
      }
      if (refExpr == null || oldExpr == null) {
        return;
      }
      final String refName = refExpr.getReferenceName();
      if (refName == null) {
        return;
      }
      PsiReplacementUtil.replaceExpression(oldExpr, myOperatorText + refName);
    }

    abstract boolean isValidPrefixExpression(@Nullable PsiPrefixExpression prefixExpr);

    abstract LocalQuickFix createFix(@NotNull String refName);

    @Nullable
    private LocalQuickFix createFix(@NotNull PsiReferenceExpression refExpr) {
      final String refName = refExpr.getReferenceName();
      if (refName == null) {
        return null;
      }
      final PsiPrefixExpression topPrefixExpr = ObjectUtils.tryCast(refExpr.getParent().getParent(), PsiPrefixExpression.class);
      if (topPrefixExpr == null) {
        return null;
      }
      final PsiType refExprType = refExpr.getType();
      if (TypeUtils.unaryNumericPromotion(refExprType) != refExprType &&
          MethodCallUtils.isNecessaryForSurroundingMethodCall(topPrefixExpr, refExpr)) {
        return null;
      }
      final PsiVariable resolved = ObjectUtils.tryCast(refExpr.resolve(), PsiVariable.class);
      if (resolved == null || resolved.hasModifierProperty(PsiModifier.FINAL)) {
        return null;
      }
      return createFix(refName);
    }

    private static boolean containsOnlyWhitespaceBetweenOperatorAndOperand(@Nullable PsiPrefixExpression prefixExpression) {
      if (prefixExpression == null) return false;
      final PsiJavaToken operator = prefixExpression.getOperationSign();
      final PsiExpression operand = prefixExpression.getOperand();
      PsiElement nextSibling = operator.getNextSibling();
      while (nextSibling != operand) {
        if (!(nextSibling instanceof PsiWhiteSpace)) {
          return false;
        }
        nextSibling = nextSibling.getNextSibling();
      }
      return true;
    }

    private static class IncrementFixManager extends IncrementDecrementFixManager {
      private IncrementFixManager(@NotNull PsiPrefixExpression prefixExpr) {
        super(prefixExpr, "++");
      }

      @Override
      boolean isValidPrefixExpression(@Nullable PsiPrefixExpression prefixExpr) {
        return prefixExpr != null && prefixExpr.getOperationTokenType().equals(JavaTokenType.PLUS);
      }

      @Override
      LocalQuickFix createFix(@NotNull String refName) {
        return new UnaryIncrementFix(refName);
      }
    }

    private static class DecrementFixManager extends IncrementDecrementFixManager {
      private DecrementFixManager(@NotNull PsiPrefixExpression prefixExpr) {
        super(prefixExpr, "--");
      }

      @Override
      boolean isValidPrefixExpression(@Nullable PsiPrefixExpression prefixExpr) {
        return prefixExpr != null && prefixExpr.getOperationTokenType().equals(JavaTokenType.MINUS);
      }

      @Override
      LocalQuickFix createFix(@NotNull String refName) {
        return new UnaryDecrementFix(refName);
      }
    }
  }
}